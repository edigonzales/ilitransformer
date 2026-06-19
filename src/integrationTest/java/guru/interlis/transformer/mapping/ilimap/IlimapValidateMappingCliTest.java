package guru.interlis.transformer.mapping.ilimap;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class IlimapValidateMappingCliTest {

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
    void validateMappingAcceptsIlimap() {
        int exitCode = new CommandLine(new CliMain())
                .execute("validate-mapping", "--mapping", "src/test/resources/mapping/ilimap/p5-1to1.ilimap");

        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("valid");
    }

    @Test
    void validateMappingWithModeldirsAcceptsIlimap() {
        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "validate-mapping",
                        "--mapping",
                        "src/test/resources/mapping/ilimap/p5-1to1.ilimap",
                        "--modeldir",
                        "src/test/data/models");

        assertThat(exitCode).isZero();
        String output = outContent.toString();
        assertThat(output).contains("valid");
    }
}
