package guru.interlis.transformer.formats;

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

/**
 * Phase 2 guard: generic format {@code options} declared on inputs/outputs must be accepted by both
 * the {@code .ilimap} and YAML pipelines without activating a new format. The built-in INTERLIS
 * provider ignores the options, so an XTF-to-XTF transform must still succeed.
 */
class FormatOptionsToleranceIntegrationTest {

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
    void ilimapMappingWithFormatOptionsIsTolerated(@TempDir Path tempDir) throws Exception {
        Path inputXtf = Path.of("src/test/resources/transfers/scalar-identity/input.xtf")
                .toAbsolutePath();
        Path output = tempDir.resolve("ilimap-output.xtf");
        Path mapping = tempDir.resolve("mapping.ilimap");
        String modelDir = Path.of("src/test/data/models").toAbsolutePath().toString();

        Files.writeString(mapping, """
                mapping v2 "options-tolerance" {
                  job {
                    failPolicy report_only;
                  }

                  input src {
                    path "INPUT_PATH";
                    model "P5Model";
                    format xtf;
                    option firstLineIsHeader true;
                    option separator ";";
                    option encoding "UTF-8";
                  }

                  output tgt {
                    path "OUTPUT_PATH";
                    model "P5Model";
                    format xtf;
                    option pretty true;
                  }

                  rule copy-rule {
                    target tgt class "P5Model.P5Topic.TargetClass";
                    source s from src class "P5Model.P5Topic.SourceClass";

                    assign {
                      Label = s.Name;
                      Size = s.Anzahl;
                      Enabled = s.Aktiv;
                    }
                  }
                }
                """.replace("INPUT_PATH", inputXtf.toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PATH", output.toString().replace("\\", "\\\\")));

        int exitCode = new CommandLine(new CliMain())
                .execute("transform", "--mapping", mapping.toString(), "--modeldir", modelDir);

        // The mapping carries generic format options on both input and output. They must be accepted
        // through the full load -> compile -> plan pipeline without breaking it.
        assertThat(exitCode).isZero();
        assertThat(outContent.toString()).contains("REPORT_ONLY");
    }

    @Test
    void yamlMappingWithFormatOptionsIsTolerated(@TempDir Path tempDir) throws Exception {
        Path inputXtf = Path.of("src/test/resources/transfers/scalar-identity/input.xtf")
                .toAbsolutePath();
        Path output = tempDir.resolve("yaml-output.xtf");
        Path mapping = tempDir.resolve("mapping.yaml");
        String modelDir = Path.of("src/test/data/models").toAbsolutePath().toString();

        Files.writeString(mapping, """
                version: 1
                job:
                  failPolicy: "report_only"
                  inputs:
                    - id: src
                      path: "INPUT_PATH"
                      model: "P5Model"
                      format: xtf
                      options:
                        firstLineIsHeader: true
                        separator: ";"
                        fetchSize: 10000
                        encoding: UTF-8
                  outputs:
                    - id: tgt
                      path: "OUTPUT_PATH"
                      model: "P5Model"
                      format: xtf
                      options:
                        pretty: true
                mapping:
                  rules:
                    - id: copy-rule
                      target:
                        output: tgt
                        class: "P5Model.P5Topic.TargetClass"
                      sources:
                        - alias: s
                          input: src
                          class: "P5Model.P5Topic.SourceClass"
                      assign:
                        Label: "s.Name"
                        Size: "s.Anzahl"
                        Enabled: "s.Aktiv"
                """.replace("INPUT_PATH", inputXtf.toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PATH", output.toString().replace("\\", "\\\\")));

        int exitCode = new CommandLine(new CliMain())
                .execute("transform", "--mapping", mapping.toString(), "--modeldir", modelDir);

        assertThat(exitCode).isZero();
        assertThat(outContent.toString()).contains("REPORT_ONLY");
    }
}
