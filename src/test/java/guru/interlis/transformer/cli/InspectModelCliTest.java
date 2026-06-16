package guru.interlis.transformer.cli;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.app.CliMain;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class InspectModelCliTest {

    @Test
    void inspectModelHelpPrintsUsage() {
        int exitCode = runCli("inspect-model", "--help");
        assertThat(exitCode).isZero();
    }

    @Test
    void inspectModelWithoutModelShowsError() {
        int exitCode = runCli("inspect-model");
        assertThat(exitCode).isNotZero();
    }

    @Test
    void inspectModelWithMinimalModelExitsZero() {
        int exitCode = runCli(
                "inspect-model",
                "--model",
                "src/test/data/models/minimal.ili",
                "--modeldir",
                "src/test/data/models/",
                "--format",
                "json");
        assertThat(exitCode).isZero();
    }

    @Test
    void inspectModelWithNonexistentModelExitsError() {
        int exitCode = runCli(
                "inspect-model",
                "--model",
                "src/test/data/models/nonexistent.ili",
                "--modeldir",
                "src/test/data/models/");
        assertThat(exitCode).isNotZero();
    }

    @Test
    void inspectModelMarkdownFormatExitsZero() {
        int exitCode = runCli(
                "inspect-model",
                "--model",
                "src/test/data/models/minimal.ili",
                "--modeldir",
                "src/test/data/models/",
                "--format",
                "markdown");
        assertThat(exitCode).isZero();
    }

    @Test
    void inspectModelBothFormatsExitsZero() {
        int exitCode = runCli(
                "inspect-model",
                "--model",
                "src/test/data/models/minimal.ili",
                "--modeldir",
                "src/test/data/models/",
                "--format",
                "both");
        assertThat(exitCode).isZero();
    }

    private static int runCli(String... args) {
        return new CommandLine(new CliMain()).execute(args);
    }
}
