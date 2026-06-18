package guru.interlis.transformer.mapping.ilimap.convert;

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

class ConvertMappingCliTest {

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
    void cliConvertsFile(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("output.ilimap");

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "convert-mapping",
                        "--from",
                        "src/test/resources/mapping/equivalence/minimal.yaml",
                        "--to",
                        outputFile.toString());

        assertThat(exitCode).isZero();
        assertThat(Files.exists(outputFile)).isTrue();
        String content = Files.readString(outputFile);
        assertThat(content).startsWith("mapping v2");
        assertThat(content).contains("input src");
        assertThat(content).contains("output tgt");
        assertThat(content).contains("rule transform-item");
    }

    @Test
    void cliRejectsNonexistentInput(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("output.ilimap");

        int exitCode = new CommandLine(new CliMain())
                .execute("convert-mapping", "--from", "nonexistent.yaml", "--to", outputFile.toString());

        assertThat(exitCode).isNotZero();
    }

    @Test
    void cliConvertsComplexYaml(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("lfp3.ilimap");

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "convert-mapping",
                        "--from",
                        "src/test/resources/mappings/dm01-to-dmav-lfp3-test.yaml",
                        "--to",
                        outputFile.toString());

        assertThat(exitCode).isZero();
        String content = Files.readString(outputFile);
        assertThat(content).contains("enum Zuverlaessigkeit_DM01_DMAV");
        assertThat(content).contains("bag Textposition");
        assertThat(content).contains("ref Entstehung");
    }
}
