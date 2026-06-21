package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class RunBundleCliTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @TempDir
    Path tempDir;

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
    void runBundleHelpSucceeds() {
        int exitCode = new CommandLine(new CliMain()).execute("run-bundle", "--help");

        assertThat(exitCode).isZero();
        assertThat(outContent.toString()).contains("--manifest");
    }

    @Test
    void runBundleFailsOnMissingModuleMapping() throws Exception {
        Path manifestPath = tempDir.resolve("manifest.yaml");
        Files.writeString(manifestPath, """
                name: cli-bundle
                description: cli demo
                direction: dm01-to-dmav
                failPolicy: strict
                source:
                  pathHint: ./source.itf
                  model: DM01AVCH24LV95D
                  format: itf
                output:
                  model: DMAVTYM_Alles_V1_1
                  format: xtf
                  fileName: out.xtf
                mapping:
                  oidStrategy: deterministicUuid
                  oidNamespace: cli-bundle
                  basketStrategy: byTopic
                  compileMode: compatible
                modeldirs:
                  - src/test/data/models
                modules:
                  - id: a
                    mapping: ./missing.yaml
                """, StandardCharsets.UTF_8);

        int exitCode = new CommandLine(new CliMain()).execute("run-bundle", "--manifest", manifestPath.toString());

        assertThat(exitCode).isNotZero();
    }
}
