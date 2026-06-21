package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class CliMainTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUpStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void helpPrintsUsageAndExitsZero() {
        int exitCode = new CommandLine(new CliMain()).execute("--help");
        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("Usage:");
        assertThat(output).contains("ilitransformer");
        assertThat(output).contains("transform");
        assertThat(output).contains("validate-mapping");
    }

    @Test
    void versionPrintsVersionAndExitsZero() {
        int exitCode = new CommandLine(new CliMain()).execute("--version");
        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("ilitransformer");
        assertThat(output).contains("0.1.0");
    }

    @Test
    void topLevelWithoutArgsPrintsHelp() {
        int exitCode = new CommandLine(new CliMain()).execute();
        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("Usage:");
    }

    @Test
    void validateMappingSubcommandShowsHelp() {
        int exitCode = new CommandLine(new CliMain()).execute("validate-mapping", "--help");
        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("validate-mapping");
        assertThat(output).contains("--mapping");
    }

    @Test
    void transformSubcommandShowsHelp() {
        int exitCode = new CommandLine(new CliMain()).execute("transform", "--help");
        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("transform");
        assertThat(output).contains("--mapping");
        assertThat(output).contains("--fail-policy");
        assertThat(output).contains("--keep-temp");
        assertThat(output).contains("--validate");
        assertThat(output).contains("--report");
    }

    @Test
    void validateMappingWithoutFileShowsError() {
        int exitCode = new CommandLine(new CliMain()).execute("validate-mapping");
        assertThat(exitCode).isEqualTo(2);
        String err = errContent.toString();
        assertThat(err).contains("--mapping");
    }

    @Test
    void validateMappingWithValidFileExitsZero() {
        int exitCode = new CommandLine(new CliMain())
                .execute("validate-mapping", "--mapping", "src/test/resources/mappings/minimal-valid.yaml");
        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("valid");
    }

}
