package guru.interlis.transformer.app;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.compiler.MappingCompiler.CompileResult;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.engine.ExecutionMetrics;
import guru.interlis.transformer.engine.ExecutionMetricsSnapshot;
import guru.interlis.transformer.engine.RuleDispatchIndex;
import guru.interlis.transformer.geometry.IoxGeometryAdapter;
import guru.interlis.transformer.loss.LossinessCollector;
import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.InMemoryParentChildIndex;
import guru.interlis.transformer.state.InMemoryReferenceIndex;
import guru.interlis.transformer.state.InMemorySourceLookupIndex;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceLookupIndex;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;
import guru.interlis.transformer.validation.TransferValidationService;
import guru.interlis.transformer.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JobRunner {

    private static final Set<String> NON_DOWNGRADEABLE_CODES = Set.of(
            DiagnosticCode.RUN_DUPLICATE_TARGET_OID,
            DiagnosticCode.MODEL_COMPILE_FAILED,
            DiagnosticCode.RUN_REF_MISSING_MANDATORY,
            DiagnosticCode.RUN_MISSING_SOURCE_OID
    );

    private final TransferValidationService validationService = new InProcessIlivalidatorService();

    public CompileResult validateMapping(Path configPath) throws Exception {
        MappingLoader loader = new MappingLoader();
        JobConfig config = loader.load(configPath);
        return new MappingCompiler().compile(config);
    }

    public PreparedJob prepare(Path mappingFile, RunOptions options) throws Exception {
        Path baseDirectory = mappingFile.toAbsolutePath().getParent();
        MappingLoader loader = new MappingLoader();
        JobConfig config = loader.load(mappingFile);

        ModelRegistry modelRegistry = ModelRegistry.builder()
                .config(config)
                .modelDirs(config.job.modeldir)
                .modelDirs(options.modelDirectories())
                .baseDirectory(baseDirectory)
                .build();

        TransformPlan plan = new MappingCompiler().compileTyped(config, modelRegistry);

        return new PreparedJob(config, plan, modelRegistry, baseDirectory);
    }

    public DiagnosticCollector run(Path configPath, String modelDir) throws Exception {
        List<String> dirs = new ArrayList<>();
        if (modelDir != null && !modelDir.isBlank()) {
            dirs.add(modelDir);
        }
        return run(configPath, new RunOptions(dirs));
    }

    /**
     * Runs a transformation with full runtime options including fail policy enforcement,
     * transactional output, optional validation, and report generation.
     */
    public DiagnosticCollector run(Path configPath, RunOptions options) throws Exception {
        PreparedJob prepared = prepare(configPath, options);
        TransformPlan plan = prepared.plan();
        JobConfig config = prepared.config();

        printCompilerDiagnostics(plan);

        if (plan.diagnostics().hasErrors()) {
            System.err.println("Compilation failed with errors. Aborting.");
            return plan.diagnostics();
        }

        if (plan.failPolicy() == FailPolicy.REPORT_ONLY) {
            System.out.println("REPORT_ONLY mode: compilation successful. Skipping transformation run.");
            if (options.reportDirectory() != null) {
                writeReports(plan, null, new DiagnosticCollector(), List.of(),
                        Duration.ZERO, Map.of(), null, options.reportDirectory());
            }
            return plan.diagnostics();
        }

        Instant start = Instant.now();
        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformResult result = null;
        ExecutionMetricsSnapshot metricsSnapshot = null;
        LossinessCollector lossinessCollector = null;
        List<ValidationResult> validationResults = new ArrayList<>();
        boolean committed = false;

        try (TransactionalOutputManager txManager = new TransactionalOutputManager(options.keepTemporaryFiles())) {
            InterlisIoFactory ioFactory = new InterlisIoFactory();

            Map<String, IoxWriter> writersByOutputId = new LinkedHashMap<>();
            Map<String, OutputBinding> tempBindings = new LinkedHashMap<>();
            for (var entry : plan.outputsById().entrySet()) {
                String outputId = entry.getKey();
                OutputBinding binding = entry.getValue();
                try {
                    Path tempPath = txManager.createTemporaryOutput(binding);
                    writersByOutputId.put(outputId,
                            ioFactory.createWriter(tempPath, binding.transferDescription(), engineDiag));
                    tempBindings.put(outputId, new OutputBinding(outputId, tempPath,
                            binding.declaredModelName(), binding.format(),
                            binding.transferDescription(), binding.typeSystem()));
                } catch (Exception e) {
                    engineDiag.add(new Diagnostic(DiagnosticCode.COMMIT_FAILED,
                            Severity.ERROR, "Failed to create writer for output " + outputId + ": " + e.getMessage(), outputId, null));
                }
            }

            Map<String, IoxReader> readerByInputId = new HashMap<>();
            for (var entry : plan.inputsById().entrySet()) {
                String inputId = entry.getKey();
                var binding = entry.getValue();
                try {
                    readerByInputId.put(inputId,
                            ioFactory.createReader(binding.path(), binding.transferDescription()));
                } catch (Exception e) {
                    engineDiag.add(new Diagnostic(DiagnosticCode.COMMIT_FAILED,
                            Severity.ERROR, "Failed to open input " + inputId + ": " + e.getMessage(), inputId, null));
                }
            }

            if (!engineDiag.hasErrors() && !readerByInputId.isEmpty()) {
                StateStore stateStore = new InMemoryStateStore();
                ReferenceIndex refIndex = new InMemoryReferenceIndex();
                SourceLookupIndex slIndex = new InMemorySourceLookupIndex();
                ParentChildIndex pcIndex = new InMemoryParentChildIndex();
                ExecutionMetrics metrics = new ExecutionMetrics();

                TransformationEngine engine = new TransformationEngine(
                        new ExpressionEngine(), stateStore, engineDiag,
                        new IoxGeometryAdapter(), new DefaultOidGenerationService(),
                        refIndex, slIndex, pcIndex, metrics);
                try {
                    result = engine.runTyped(plan, readerByInputId::get, writersByOutputId);
                    metricsSnapshot = engine.getMetricsSnapshot();
                    lossinessCollector = engine.getLossinessCollector();
                } catch (Exception e) {
                    engineDiag.add(new Diagnostic(DiagnosticCode.COMMIT_FAILED,
                            Severity.ERROR,
                            "Transformation engine failed: " + e.getClass().getSimpleName()
                                    + (e.getMessage() != null ? ": " + e.getMessage() : ""),
                            null, null));
                }
            }

            // TransformationEngine owns normal writer finalization. If execution failed before
            // the output service could close the writers, close any remaining handles here.
            if (result == null) {
                for (IoxWriter writer : writersByOutputId.values()) {
                    try {
                        writer.flush();
                        writer.close();
                    } catch (Exception e) {
                        engineDiag.add(new Diagnostic(DiagnosticCode.COMMIT_FAILED,
                                Severity.ERROR, "Failed to close writer: " + e.getMessage(), null, null));
                    }
                }
            }

            // Validate outputs if requested
            if (options.validateOutput()) {
                for (var entry : tempBindings.entrySet()) {
                    String outputId = entry.getKey();
                    OutputBinding binding = entry.getValue();
                    OutputBinding realBinding = plan.outputsById().get(outputId);
                    if (realBinding == null) continue;

                    ValidationResult vr = validationService.validate(
                            binding.path(),
                            options.modelDirectories(),
                            List.of(realBinding.declaredModelName()),
                            options.reportDirectory() != null
                                    ? options.reportDirectory().resolve(outputId + "-validation.log")
                                    : null);
                    validationResults.add(vr);

                    if (!vr.valid()) {
                        engineDiag.add(new Diagnostic(DiagnosticCode.VALIDATION_FAILED,
                                Severity.ERROR,
                                "Validator reported errors for output " + outputId
                                        + " (model: " + realBinding.declaredModelName() + ")",
                                outputId, "See validation log for details"));
                    }
                }
            }

            // Decide commit/rollback based on fail policy
            boolean hasNonDowngradeableErrors = hasNonDowngradeableErrors(engineDiag);
            boolean hasAnyErrors = engineDiag.hasErrors();

            boolean shouldCommit = switch (plan.failPolicy()) {
                case STRICT -> !hasAnyErrors;
                case LENIENT -> !hasNonDowngradeableErrors;
                case REPORT_ONLY -> false;
            };

            if (shouldCommit) {
                for (var entry : plan.outputsById().entrySet()) {
                    String outputId = entry.getKey();
                    if (txManager.tempPath(outputId) != null) {
                        txManager.commit(outputId);
                    }
                }
                committed = true;
            } else {
                engineDiag.add(new Diagnostic(DiagnosticCode.COMMIT_ROLLED_BACK,
                        Severity.ERROR,
                        "Output rolled back due to errors (failPolicy="
                                + plan.failPolicy().name().toLowerCase() + ")",
                        null, "Fix errors and retry, or use --keep-temp to inspect temporary files"));
                txManager.rollbackAll();
            }
        }

        // Merge engine diagnostics into plan
        for (Diagnostic d : engineDiag.all()) {
            plan.diagnostics().add(d);
        }

        Duration elapsed = Duration.between(start, Instant.now());

        if (result != null) {
            System.out.println(result.summary());
        }
        if (metricsSnapshot != null && metricsSnapshot.elapsedMillis() > 0) {
            System.out.println(metricsSnapshot.summary());
        }
        if (committed) {
            System.out.println("Output committed successfully.");
        }

        // Write reports
        if (options.reportDirectory() != null) {
            Map<String, String> modelVersions = collectModelVersions(plan);
            writeReports(plan, result, engineDiag, validationResults, elapsed, modelVersions,
                    metricsSnapshot, options.reportDirectory());
            writeLossinessReports(lossinessCollector, options.reportDirectory());
        }

        return plan.diagnostics();
    }

    // -- Fail policy helpers -------------------------------------------------

    private static boolean hasNonDowngradeableErrors(DiagnosticCollector diagnostics) {
        return diagnostics.all().stream()
                .anyMatch(d -> d.severity() == Severity.ERROR
                        && (d.code() == null
                        || NON_DOWNGRADEABLE_CODES.contains(d.code())
                        || d.code().startsWith("ILITRF-MODEL-")
                        || d.code().startsWith("ILITRF-MAP-")));
    }

    // -- Internal helpers ----------------------------------------------------

    private static void printCompilerDiagnostics(TransformPlan plan) {
        if (plan.diagnostics().all().isEmpty()) return;
        System.out.println("--- Compiler Diagnostics ---");
        for (Diagnostic d : plan.diagnostics().all()) {
            System.out.printf("[%s] %s: %s (rule: %s)%n",
                    d.severity(), d.code(), d.message(),
                    d.sourcePath() != null ? d.sourcePath() : "");
            if (d.suggestion() != null) {
                System.out.printf("  Suggestion: %s%n", d.suggestion());
            }
        }
        System.out.println();
    }

    private void writeReports(TransformPlan plan, TransformResult result,
                               DiagnosticCollector diagnostics,
                               List<ValidationResult> validationResults,
                               Duration elapsed, Map<String, String> modelVersions,
                               ExecutionMetricsSnapshot metricsSnapshot,
                               Path reportDirectory) {
        try {
            TransformationReportWriter reportWriter = new TransformationReportWriter();
            TransformResult safeResult = result != null ? result
                    : new TransformResult(0, 0, 0, 0, 0, 0, "-", "-");

            reportWriter.writeJson(reportDirectory.resolve("transformation-report.json"),
                    plan, safeResult, diagnostics, validationResults, elapsed, modelVersions,
                    metricsSnapshot);
            reportWriter.writeMarkdown(reportDirectory.resolve("transformation-report.md"),
                    plan, safeResult, diagnostics, validationResults, elapsed, modelVersions,
                    metricsSnapshot);
            System.out.println("Reports written to: " + reportDirectory);
        } catch (IOException e) {
            System.err.println("Failed to write reports: " + e.getMessage());
        }
    }

    private void writeLossinessReports(LossinessCollector collector, Path reportDirectory) {
        if (collector == null || collector.isEmpty()) {
            return;
        }
        try {
            collector.writeJson(reportDirectory.resolve("lossiness-report.json"));
            collector.writeMarkdown(reportDirectory.resolve("lossiness-report.md"));
        } catch (IOException e) {
            System.err.println("Failed to write lossiness reports: " + e.getMessage());
        }
    }

    private static Map<String, String> collectModelVersions(TransformPlan plan) {
        Map<String, String> versions = new LinkedHashMap<>();
        for (var entry : plan.inputsById().entrySet()) {
            var binding = entry.getValue();
            if (binding.transferDescription() != null) {
                var modelIt = binding.transferDescription().iterator();
                while (modelIt.hasNext()) {
                    var model = modelIt.next();
                            versions.putIfAbsent(model.getName(),
                                    model.getModelVersion() != null ? model.getModelVersion() : "unknown");
                }
            }
        }
        for (var entry : plan.outputsById().entrySet()) {
            var binding = entry.getValue();
            if (binding.transferDescription() != null) {
                var modelIt = binding.transferDescription().iterator();
                while (modelIt.hasNext()) {
                    var model = modelIt.next();
                            versions.putIfAbsent(model.getName(),
                                    model.getModelVersion() != null ? model.getModelVersion() : "unknown");
                }
            }
        }
        return versions;
    }
}
