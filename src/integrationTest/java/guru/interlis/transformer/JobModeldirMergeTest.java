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

class JobModeldirMergeTest {

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
    void cliModeldirMergesWithYamlModeldir(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.itf");
        Path output = tempDir.resolve("output.itf");
        Path mapping = tempDir.resolve("mapping.yaml");

        Files.writeString(input, """
                SCPI TestModel TestTopic TestClass
                MTID 001 oid1
                MODL TestModel
                OBJE oid1 TestModel.TestTopic.TestClass
                NAME Merged
                """);

        Files.writeString(
                mapping, """
                version: 1
                job:
                  description: "Modeldir merge test"
                  failPolicy: "report_only"
                  modeldir:
                    - "src/test/data/models"
                  inputs:
                    - id: in1
                      path: "INPUT_PLACEHOLDER"
                      model: "TestModel"
                  outputs:
                    - id: out1
                      path: "OUTPUT_PLACEHOLDER"
                      model: "TestModel"
                mapping:
                  rules:
                    - id: rule1
                      target:
                        output: out1
                        class: "TestModel.TestTopic.TestClass"
                      sources:
                        - alias: s
                          input: in1
                          class: "TestModel.TestTopic.TestClass"
                      assign:
                        Name: "${s.Name}"
                """.replace("INPUT_PLACEHOLDER", input.toString().replace("\\", "\\\\"))
                        .replace("OUTPUT_PLACEHOLDER", output.toString().replace("\\", "\\\\")));

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toAbsolutePath().toString(),
                        "--modeldir",
                        "src/test/data/av/models");

        assertThat(exitCode).isZero();
    }

    @Test
    void multipleCliModeldirsAreMerged(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.itf");
        Path output = tempDir.resolve("output.itf");
        Path mapping = tempDir.resolve("mapping.yaml");

        Files.writeString(input, """
                SCPI TestModel TestTopic TestClass
                MTID 001 oid1
                MODL TestModel
                OBJE oid1 TestModel.TestTopic.TestClass
                NAME Multi
                """);

        Files.writeString(
                mapping, """
                version: 1
                job:
                  description: "Multiple modeldir test"
                  failPolicy: "report_only"
                  inputs:
                    - id: in1
                      path: "INPUT_PLACEHOLDER"
                      model: "TestModel"
                  outputs:
                    - id: out1
                      path: "OUTPUT_PLACEHOLDER"
                      model: "TestModel"
                mapping:
                  rules:
                    - id: rule1
                      target:
                        output: out1
                        class: "TestModel.TestTopic.TestClass"
                      sources:
                        - alias: s
                          input: in1
                          class: "TestModel.TestTopic.TestClass"
                      assign:
                        Name: "${s.Name}"
                """.replace("INPUT_PLACEHOLDER", input.toString().replace("\\", "\\\\"))
                        .replace("OUTPUT_PLACEHOLDER", output.toString().replace("\\", "\\\\")));

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toAbsolutePath().toString(),
                        "--modeldir",
                        "src/test/data/models",
                        "--modeldir",
                        "src/test/data/av/models");

        assertThat(exitCode).isZero();
    }

    @Test
    void modeldirOptionAppearsInHelp() {
        int exitCode = new CommandLine(new CliMain()).execute("transform", "--help");
        assertThat(exitCode).isZero();
        String stdout = outContent.toString();
        assertThat(stdout).contains("--modeldir");
    }
}
