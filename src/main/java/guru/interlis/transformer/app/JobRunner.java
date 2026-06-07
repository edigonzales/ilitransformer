package guru.interlis.transformer.app;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.interlis.InterlisModelLoader;
import guru.interlis.transformer.mapping.compiler.CompilerReport;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.compiler.MappingCompiler.CompileResult;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class JobRunner {

    public CompileResult validateMapping(Path configPath) throws Exception {
        MappingLoader loader = new MappingLoader();
        JobConfig config = loader.load(configPath);
        return new MappingCompiler().compile(config);
    }

    public DiagnosticCollector run(Path configPath, String modelDir) throws Exception {
        MappingLoader loader = new MappingLoader();
        JobConfig config = loader.load(configPath);

        // Compile models
        InterlisModelLoader modelLoader = new InterlisModelLoader();
        Map<String, TransferDescription> tdByModel = new HashMap<>();
        for (JobConfig.InputSpec input : config.job.inputs) {
            tdByModel.computeIfAbsent(input.model, m -> loadModel(modelLoader, m, modelDir));
        }
        for (JobConfig.OutputSpec output : config.job.outputs) {
            tdByModel.computeIfAbsent(output.model, m -> loadModel(modelLoader, m, modelDir));
        }

        // Build TypeSystemFacades
        Map<String, TypeSystemFacade> sourceTypeSystems = new HashMap<>();
        Map<String, TypeSystemFacade> targetTypeSystems = new HashMap<>();
        for (JobConfig.InputSpec input : config.job.inputs) {
            TransferDescription td = tdByModel.get(input.model);
            if (td != null) {
                sourceTypeSystems.putIfAbsent(input.model, new TypeSystemFacade(td));
            }
        }
        for (JobConfig.OutputSpec output : config.job.outputs) {
            TransferDescription td = tdByModel.get(output.model);
            if (td != null) {
                targetTypeSystems.putIfAbsent(output.model, new TypeSystemFacade(td));
            }
        }

        // Compile mapping (Phase 3 typed compilation)
        MappingCompiler compiler = new MappingCompiler();
        TransformPlan plan = compiler.compileTyped(config, sourceTypeSystems, targetTypeSystems);

        // Print compiler diagnostics
        if (!plan.diagnostics().all().isEmpty()) {
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

        if (plan.diagnostics().hasErrors()) {
            System.err.println("Compilation failed with errors. Aborting.");
            return plan.diagnostics();
        }

        // Create I/O
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        Map<String, IoxWriter> writersByOutputId = new HashMap<>();
        for (JobConfig.OutputSpec output : config.job.outputs) {
            writersByOutputId.put(output.id,
                    ioFactory.createWriter(Path.of(output.path), tdByModel.get(output.model)));
        }

        // Build reader factory
        Map<String, IoxReader> readerByInputId = new HashMap<>();
        for (JobConfig.InputSpec input : config.job.inputs) {
            readerByInputId.put(input.id,
                    ioFactory.createReader(Path.of(input.path), tdByModel.get(input.model)));
        }

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(new ExpressionEngine(),
                new InMemoryStateStore(), engineDiag);
        engine.runTyped(plan, readerByInputId::get, writersByOutputId);

        // Merge engine diagnostics into compiler diagnostics
        for (Diagnostic d : engineDiag.all()) {
            plan.diagnostics().add(d);
        }

        return plan.diagnostics();
    }

    private TransferDescription loadModel(InterlisModelLoader modelLoader, String modelName, String modelDir) {
        try {
            return modelLoader.compileModel(modelName, modelDir);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compile model " + modelName, ex);
        }
    }
}
