package guru.interlis.transformer.app;

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
    version = "ili-transformer 0.1.0"
)
public final class CliMain implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Mapping YAML configuration file"
    )
    private Path mapping;

    @Option(
        names = {"--modeldir"},
        description = "Model directory path for INTERLIS model resolution"
    )
    private String modelDir;

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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliMain()).execute(args);
        System.exit(exitCode);
    }
}
