package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ValidateMappingTypedCliTest {

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
    void validateMappingWithModeldirsPerformsFullValidation() {
        int exitCode = new CommandLine(new CliMain()).execute(
                "validate-mapping",
                "--mapping", "src/test/resources/mappings/p25/simple-identity.yaml",
                "--modeldir", "src/test/data/models"
        );

        String output = outContent.toString();
        assertThat(output).contains("model-aware");
    }

    @Test
    void validateMappingWithoutModeldirsPerformsBasicValidation() {
        int exitCode = new CommandLine(new CliMain()).execute(
                "validate-mapping",
                "--mapping", "src/test/resources/mappings/p25/simple-identity.yaml"
        );

        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("valid");
    }
}
