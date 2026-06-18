package guru.interlis.transformer.mapping.ilimap;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class IlimapDiagnosticsCliTest {

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
    void validateMappingReportsLineAndColumnForSyntaxError() {
        int exitCode = new CommandLine(new CliMain())
                .execute("validate-mapping", "--mapping", "src/test/resources/mapping/ilimap/syntax-error.ilimap");

        assertThat(exitCode).isNotZero();
        String allOutput = outContent.toString() + errContent.toString();
        assertThat(allOutput).contains("syntax-error.ilimap");
        assertThat(allOutput).containsPattern("\\d+:\\d+");
    }

    @Test
    void validateMappingReportsUnknownSymbol() {
        int exitCode = new CommandLine(new CliMain())
                .execute("validate-mapping", "--mapping", "src/test/resources/mapping/ilimap/unknown-symbol.ilimap");

        assertThat(exitCode).isNotZero();
        String allOutput = outContent.toString() + errContent.toString();
        assertThat(allOutput).contains("unknown-symbol.ilimap");
        assertThat(allOutput).containsAnyOf("UNKNOWN", "unknown output");
    }

    @Test
    void transformReportsSyntaxErrorForIlimap() {
        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        "src/test/resources/mapping/ilimap/syntax-error.ilimap",
                        "--modeldir",
                        "src/test/data/models");

        assertThat(exitCode).isNotZero();
        String allOutput = outContent.toString() + errContent.toString();
        assertThat(allOutput).contains("syntax-error.ilimap");
    }
}
