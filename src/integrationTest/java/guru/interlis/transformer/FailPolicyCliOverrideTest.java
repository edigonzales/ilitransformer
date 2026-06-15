package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;
import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.PreparedJob;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.plan.FailPolicy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class FailPolicyCliOverrideTest {

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
    void cliFailPolicyLenientOverridesYamlReportOnly(@TempDir Path tempDir) throws Exception {
        Path mapping = tempDir.resolve("mapping.yaml");

        Files.writeString(mapping, """
                version: 1
                job:
                  description: "Override test"
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
                """
                .replace("INPUT_PLACEHOLDER", tempDir.resolve("input.itf").toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PLACEHOLDER", tempDir.resolve("output.itf").toString().replace("\\", "\\\\")));

        var options = new RunOptions(List.of(), false, null,
                false, FailPolicy.LENIENT);
        PreparedJob prepared = new JobRunner().prepare(mapping, options);

        assertThat(prepared.plan().failPolicy()).isEqualTo(FailPolicy.LENIENT);
    }

    @Test
    void cliFailPolicyReportOnlyOverridesYamlStrict(@TempDir Path tempDir) throws Exception {
        Path mapping = tempDir.resolve("mapping.yaml");

        Files.writeString(mapping, """
                version: 1
                job:
                  description: "Override test"
                  failPolicy: "strict"
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
                """
                .replace("INPUT_PLACEHOLDER", tempDir.resolve("input.itf").toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PLACEHOLDER", tempDir.resolve("output.itf").toString().replace("\\", "\\\\")));

        var options = new RunOptions(List.of(), false, null,
                false, FailPolicy.REPORT_ONLY);
        PreparedJob prepared = new JobRunner().prepare(mapping, options);

        assertThat(prepared.plan().failPolicy()).isEqualTo(FailPolicy.REPORT_ONLY);
    }

    @Test
    void yamlFailPolicyUsedWhenNoCliOverride(@TempDir Path tempDir) throws Exception {
        Path mapping = tempDir.resolve("mapping.yaml");

        Files.writeString(mapping, """
                version: 1
                job:
                  description: "No override test"
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
                """
                .replace("INPUT_PLACEHOLDER", tempDir.resolve("input.itf").toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PLACEHOLDER", tempDir.resolve("output.itf").toString().replace("\\", "\\\\")));

        var options = new RunOptions(List.of(), false, null, false);
        PreparedJob prepared = new JobRunner().prepare(mapping, options);

        assertThat(prepared.plan().failPolicy()).isEqualTo(FailPolicy.REPORT_ONLY);
    }

    @Test
    void cliFailPolicyInvalidGivesExitCode1(@TempDir Path tempDir) throws Exception {
        Path mapping = tempDir.resolve("mapping.yaml");

        Files.writeString(mapping, """
                version: 1
                job:
                  description: "Invalid test"
                  failPolicy: "strict"
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
                """
                .replace("INPUT_PLACEHOLDER", tempDir.resolve("input.itf").toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PLACEHOLDER", tempDir.resolve("output.itf").toString().replace("\\", "\\\\")));

        int exitCode = new CommandLine(new CliMain()).execute(
                "transform",
                "--mapping", mapping.toString(),
                "--fail-policy", "invalid"
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(errContent.toString()).contains("failPolicy");
    }

    @Test
    void failPolicyAppearsInHelp() {
        int exitCode = new CommandLine(new CliMain()).execute("transform", "--help");
        assertThat(exitCode).isZero();
        String stdout = outContent.toString();
        assertThat(stdout).contains("--fail-policy");
    }
}
