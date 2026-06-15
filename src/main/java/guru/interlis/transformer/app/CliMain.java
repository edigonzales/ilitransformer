package guru.interlis.transformer.app;

import guru.interlis.transformer.cli.ImportCorrelationCommand;
import guru.interlis.transformer.cli.InspectModelCommand;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.FailPolicyParser;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;
import guru.interlis.transformer.validation.TransferValidationService;
import guru.interlis.transformer.validation.ValidationResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "ilitransformer",
    description = "Generic INTERLIS transformation engine",
    mixinStandardHelpOptions = true,
    version = "ilitransformer 0.1.0",
    subcommands = {
        CliMain.TransformCommand.class,
        CliMain.ValidateMappingCommand.class,
        CliMain.ValidateTransferCommand.class,
        InspectModelCommand.class,
        ImportCorrelationCommand.class
    }
)
public final class CliMain implements Callable<Integer> {

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliMain()).execute(args);
        System.exit(exitCode);
    }

    // -- TransformCommand --------------------------------------------------

    @Command(
        name = "transform",
        description = "Run an INTERLIS transformation",
        mixinStandardHelpOptions = true
    )
    static final class TransformCommand implements Callable<Integer> {

        @Option(
            names = {"-m", "--mapping"},
            required = true,
            description = "Mapping YAML configuration file"
        )
        private Path mapping;

        @Option(
            names = {"--modeldir"},
            description = "Model directory path for INTERLIS model resolution (can be specified multiple times)"
        )
        private List<String> modeldirs = new ArrayList<>();

        @Option(
            names = {"--validate"},
            description = "Run ilivalidator on the output after transformation"
        )
        private boolean validate;

        @Option(
            names = {"--report"},
            description = "Output directory for transformation reports (JSON and Markdown)"
        )
        private Path report;

        @Option(
            names = {"--fail-policy"},
            description = "Error handling policy: strict, lenient, or report_only (default: strict)"
        )
        private String failPolicy;

        @Option(
            names = {"--keep-temp"},
            description = "Keep temporary output files on failure for debugging"
        )
        private boolean keepTemp;

        @Override
        public Integer call() throws Exception {
            FailPolicy override = null;
            if (failPolicy != null && !failPolicy.isBlank()) {
                try {
                    override = FailPolicyParser.parseOrDefault(failPolicy, null);
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    return 1;
                }
            }

            RunOptions options = new RunOptions(
                    modeldirs != null ? modeldirs : List.of(),
                    validate,
                    report,
                    keepTemp,
                    override);

            DiagnosticCollector diagnostics = new JobRunner().run(mapping, options);

            for (Diagnostic d : diagnostics.all()) {
                String level = d.severity().name();
                if (level.equals("INFO")) continue;
                System.out.printf("[%s] %s: %s (%s)%n",
                        level, d.code(), d.message(),
                        d.sourcePath() != null ? d.sourcePath() : "");
            }

            return diagnostics.hasErrors() ? 1 : 0;
        }
    }

    // -- ValidateMappingCommand --------------------------------------------

    @Command(
        name = "validate-mapping",
        description = "Validate a mapping configuration without executing a transformation",
        mixinStandardHelpOptions = true
    )
    static final class ValidateMappingCommand implements Callable<Integer> {

        @Option(
            names = {"-m", "--mapping"},
            required = true,
            description = "Mapping YAML configuration file"
        )
        private Path mapping;

        @Option(
            names = {"--modeldir"},
            description = "Model directory path for INTERLIS model resolution (can be specified multiple times)"
        )
        private List<String> modeldirs = new ArrayList<>();

        @Override
        public Integer call() throws Exception {
            if (modeldirs != null && !modeldirs.isEmpty()) {
                var options = new RunOptions(modeldirs);
                var prepared = new JobRunner().prepare(mapping, options);
                var diag = prepared.plan().diagnostics();

                if (diag.all().isEmpty()) {
                    System.out.println("Mapping is valid (full model-aware validation).");
                    return 0;
                }
                for (Diagnostic d : diag.all()) {
                    System.out.printf("[%s] %s: %s%n", d.severity(), d.code(), d.message());
                    if (d.suggestion() != null) {
                        System.out.printf("  Suggestion: %s%n", d.suggestion());
                    }
                }
                return diag.hasErrors() ? 1 : 0;
            }

            var result = new JobRunner().validateMapping(mapping);
            if (result.diagnostics().all().isEmpty()) {
                System.out.println("Mapping is valid (basic structural validation).");
                return 0;
            }
            for (Diagnostic d : result.diagnostics().all()) {
                System.out.printf("[%s] %s: %s%n", d.severity(), d.code(), d.message());
            }
            return result.diagnostics().hasErrors() ? 1 : 0;
        }
    }

    // -- ValidateTransferCommand -------------------------------------------

    @Command(
        name = "validate-transfer",
        description = "Validate an INTERLIS transfer file against its model",
        mixinStandardHelpOptions = true
    )
    static final class ValidateTransferCommand implements Callable<Integer> {

        @Option(
            names = {"-f", "--file"},
            required = true,
            description = "INTERLIS transfer file (ITF or XTF)"
        )
        private Path file;

        @Option(
            names = {"--modeldir"},
            required = true,
            description = "Model directory path for INTERLIS model resolution (can be specified multiple times)"
        )
        private List<String> modeldirs;

        @Option(
            names = {"--model"},
            required = true,
            description = "INTERLIS model name (can be specified multiple times)"
        )
        private List<String> models;

        @Option(
            names = {"--log"},
            description = "Path for validation log output"
        )
        private Path log;

        private static final TransferValidationService validationService =
                new InProcessIlivalidatorService();

        @Override
        public Integer call() throws Exception {
            ValidationResult result = validationService.validate(file, modeldirs, models, log);

            System.out.println(result.valid() ? "VALIDATION OK" : "VALIDATION FAILED");
            if (result.errorCount() >= 0) {
                System.out.printf("Errors: %d, Warnings: %d%n", result.errorCount(), result.warningCount());
            }
            if (result.logFile() != null) {
                System.out.println("Validation log: " + result.logFile());
            }
            if (!result.logText().isBlank()) {
                System.out.println(result.logText());
            }
            return result.valid() ? 0 : 1;
        }
    }
}
