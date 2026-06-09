package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.DiagnosticCollector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportOnlyTest {

    @Test
    void reportOnlySkipsTransformationAndNoOutput(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.itf");
        Path output = tempDir.resolve("output.itf");
        Path mapping = tempDir.resolve("mapping.yaml");

        Files.writeString(input, """
                SCPI TestModel TestTopic TestClass
                MTID 001 oid1
                MODL TestModel
                OBJE oid1 TestModel.TestTopic.TestClass
                NAME Hello
                """);

        Files.writeString(mapping, """
                version: 1
                job:
                  description: "Report only test"
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
                """
                .replace("INPUT_PLACEHOLDER", input.toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PLACEHOLDER", output.toString().replace("\\", "\\\\")));

        var options = new RunOptions(List.of("src/test/data/models"), false, null, false);
        DiagnosticCollector diag = new JobRunner().run(mapping, options);

        assertThat(diag.hasErrors()).isFalse();
        assertThat(output).doesNotExist();
    }

    @Test
    void reportOnlyWithReportDirectoryWritesReports(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.itf");
        Path output = tempDir.resolve("output.itf");
        Path mapping = tempDir.resolve("mapping.yaml");
        Path reportDir = tempDir.resolve("reports");

        Files.writeString(input, """
                SCPI TestModel TestTopic TestClass
                MTID 001 oid1
                MODL TestModel
                OBJE oid1 TestModel.TestTopic.TestClass
                NAME Hello
                """);

        Files.writeString(mapping, """
                version: 1
                job:
                  description: "Report only with dir"
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
                """
                .replace("INPUT_PLACEHOLDER", input.toString().replace("\\", "\\\\"))
                .replace("OUTPUT_PLACEHOLDER", output.toString().replace("\\", "\\\\")));

        var options = new RunOptions(List.of("src/test/data/models"), false, reportDir, false);
        DiagnosticCollector diag = new JobRunner().run(mapping, options);

        assertThat(diag.hasErrors()).isFalse();
        assertThat(output).doesNotExist();
        assertThat(reportDir.resolve("transformation-report.json")).exists();
        assertThat(reportDir.resolve("transformation-report.md")).exists();
    }
}
