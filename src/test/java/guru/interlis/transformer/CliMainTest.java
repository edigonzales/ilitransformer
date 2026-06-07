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
        assertThat(output).contains("ili-transformer");
        assertThat(output).contains("<mapping>");
        assertThat(output).contains("--modeldir");
    }

    @Test
    void versionPrintsVersionAndExitsZero() {
        int exitCode = new CommandLine(new CliMain()).execute("--version");
        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("0.1.0");
    }

    @Test
    void missingMappingArgShowsError() {
        int exitCode = new CommandLine(new CliMain()).execute();
        assertThat(exitCode).isEqualTo(2);
        String output = errContent.toString();
        assertThat(output).contains("Missing required parameter");
    }
}
