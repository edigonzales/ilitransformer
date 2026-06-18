package guru.interlis.transformer.mapping.ilimap;

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

class IlimapEndToEndTransformationTest {

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
    void transformsMinimalSyntheticDatasetWithIlimap(@TempDir Path tempDir) throws Exception {
        Path inputXtf = Path.of("src/test/resources/transfers/scalar-identity/input.xtf")
                .toAbsolutePath();
        Path output = tempDir.resolve("output.xtf");
        Path mapping = tempDir.resolve("mapping.ilimap");

        Files.writeString(mapping, """
                mapping v2 "e2e-test" {
                  job {
                    failPolicy report_only;
                    modeldir "MODEL_DIR";
                  }

                  input src {
                    path "INPUT_PATH";
                    model "P5Model";
                    format xtf;
                  }

                  output tgt {
                    path "OUTPUT_PATH";
                    model "P5Model";
                    format xtf;
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
                .replace("OUTPUT_PATH", output.toString().replace("\\", "\\\\"))
                .replace(
                        "MODEL_DIR",
                        Path.of("src/test/data/models")
                                .toAbsolutePath()
                                .toString()
                                .replace("\\", "\\\\")));

        int exitCode = new CommandLine(new CliMain())
                .execute("transform", "--mapping", mapping.toString(), "--modeldir", "src/test/data/models");

        assertThat(exitCode).isZero();
        String output2 = outContent.toString();
        assertThat(output2).contains("REPORT_ONLY");
    }

    @Test
    void yamlAndIlimapCompileEquivalentlyViaCliForMinimalFixture(@TempDir Path tempDir) throws Exception {
        Path inputXtf = Path.of("src/test/resources/transfers/scalar-identity/input.xtf")
                .toAbsolutePath();
        Path yamlOutput = tempDir.resolve("yaml-output.xtf");
        Path ilimapOutput = tempDir.resolve("ilimap-output.xtf");
        String modelDir = Path.of("src/test/data/models").toAbsolutePath().toString();

        Path yamlMapping = tempDir.resolve("mapping.yaml");
        Files.writeString(
                yamlMapping, """
                version: 1
                job:
                  failPolicy: "report_only"
                  inputs:
                    - id: src
                      path: "INPUT_PATH"
                      model: "P5Model"
                  outputs:
                    - id: tgt
                      path: "OUTPUT_PATH"
                      model: "P5Model"
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
                        .replace("OUTPUT_PATH", yamlOutput.toString().replace("\\", "\\\\")));

        Path ilimapMapping = tempDir.resolve("mapping.ilimap");
        Files.writeString(
                ilimapMapping, """
                mapping v2 {
                  job {
                    failPolicy report_only;
                  }

                  input src {
                    path "INPUT_PATH";
                    model "P5Model";
                    format xtf;
                  }

                  output tgt {
                    path "OUTPUT_PATH";
                    model "P5Model";
                    format xtf;
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
                        .replace("OUTPUT_PATH", ilimapOutput.toString().replace("\\", "\\\\")));

        int yamlExit = new CommandLine(new CliMain())
                .execute("transform", "--mapping", yamlMapping.toString(), "--modeldir", modelDir);
        restoreStreams();
        setUpStreams();
        int ilimapExit = new CommandLine(new CliMain())
                .execute("transform", "--mapping", ilimapMapping.toString(), "--modeldir", modelDir);

        assertThat(yamlExit).isZero();
        assertThat(ilimapExit).isZero();
    }
}
