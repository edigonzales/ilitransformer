package guru.interlis.transformer.app;

import guru.interlis.transformer.cli.GenerateMappingCommand;
import guru.interlis.transformer.cli.ImportCorrelationCommand;
import guru.interlis.transformer.cli.InspectModelCommand;
import guru.interlis.transformer.diag.Diagnostic;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "ili-transformer",
    description = "Generic INTERLIS transformation engine",
    mixinStandardHelpOptions = true,
    version = "ili-transformer 0.1.0",
    subcommands = {
        CliMain.TransformCommand.class,
        CliMain.ValidateMappingCommand.class,
        InspectModelCommand.class,
        ImportCorrelationCommand.class,
        GenerateMappingCommand.class
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
            description = "Model directory path for INTERLIS model resolution"
        )
        private String modelDir;

        @Option(
            names = {"--validate"},
            description = "Run ilivalidator on the output after transformation"
        )
        private boolean validate;

        @Option(
            names = {"--report"},
            description = "Output directory for transformation reports"
        )
        private Path report;

        @Override
        public Integer call() throws Exception {
            var diagnostics = new JobRunner().run(mapping, modelDir);
            for (Diagnostic d : diagnostics.all()) {
                System.out.printf("[%s] %s: %s (%s)%n",
                        d.severity(), d.code(), d.message(),
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

        @Override
        public Integer call() throws Exception {
            var result = new JobRunner().validateMapping(mapping);
            if (result.diagnostics().all().isEmpty()) {
                System.out.println("Mapping is valid.");
                return 0;
            }
            for (Diagnostic d : result.diagnostics().all()) {
                System.out.printf("[%s] %s: %s%n", d.severity(), d.code(), d.message());
            }
            return result.diagnostics().hasErrors() ? 1 : 0;
        }
    }
}
