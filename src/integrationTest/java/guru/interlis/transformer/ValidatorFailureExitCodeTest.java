package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ValidatorFailureExitCodeTest {

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
    void validateTransferWithInvalidFileExitsNonZero(@TempDir Path tempDir) throws Exception {
        Path invalidFile = tempDir.resolve("invalid.xtf");
        Files.writeString(invalidFile, "this is not a valid XTF file");

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "validate-transfer",
                        "--file",
                        invalidFile.toString(),
                        "--modeldir",
                        "src/test/data/models",
                        "--model",
                        "TestModel");

        assertThat(exitCode).isNotZero();
        String output = outContent.toString();
        assertThat(output).contains("VALIDATION FAILED");
    }

    @Test
    void validateTransferWithoutRequiredArgsExitsError() {
        int exitCode = new CommandLine(new CliMain()).execute("validate-transfer");

        assertThat(exitCode).isNotZero();
    }

    @Test
    void validateTransferSubcommandShowsHelp() {
        int exitCode = new CommandLine(new CliMain()).execute("validate-transfer", "--help");

        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("validate-transfer");
        assertThat(output).contains("--file");
        assertThat(output).contains("--modeldir");
        assertThat(output).contains("--model");
    }
}
