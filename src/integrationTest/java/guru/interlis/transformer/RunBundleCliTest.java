package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;
import guru.interlis.transformer.cli.RunBundleCommand;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelService;

import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertThat(outContent.toString()).contains("--manifest").contains("--[no-]validate");
    }

    @Test
    void runBundleParsesNegatableValidateOptions() throws Exception {
        assertValidateOption(null, "--manifest", "bundle.yaml");
        assertValidateOption(Boolean.TRUE, "--manifest", "bundle.yaml", "--validate");
        assertValidateOption(Boolean.FALSE, "--manifest", "bundle.yaml", "--no-validate");
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

    @Test
    void runBundleNoValidateOverridesManifestValidation() throws Exception {
        Path manifestPath = tempDir.resolve("manifest.yaml");
        Path outputPath = tempDir.resolve("output.xtf");
        Path reportDir = tempDir.resolve("report");
        Path inputPath = tempDir.resolve("input.xtf");
        writeP5Input(inputPath);
        Path modelDir = Path.of("src/test/data/models").toAbsolutePath();
        Path mappingPath = tempDir.resolve("mapping.yaml");

        Files.writeString(mappingPath, """
                version: 1
                job:
                  name: cli-module
                  inputs:
                    - id: source
                      path: unused.itf
                      model: P5Model
                  outputs:
                    - id: target
                      path: unused-output.xtf
                      model: P5Model
                mapping:
                  rules:
                    - id: copy-rule
                      target:
                        output: target
                        class: P5Model.P5Topic.TargetClass
                      sources:
                        - alias: src
                          input: source
                          class: P5Model.P5Topic.SourceClass
                      assign:
                        Label: ${src.Name}
                        Size: ${src.Anzahl}
                        Enabled: ${src.Aktiv}
                """, StandardCharsets.UTF_8);
        Files.writeString(
                manifestPath,
                """
                name: cli-bundle
                description: cli demo
                direction: identity
                failPolicy: strict
                validate: true
                source:
                  id: source
                  pathHint: SOURCE_PATH
                  model: P5Model
                  format: xtf
                output:
                  id: target
                  model: P5Model
                  format: xtf
                  fileName: output.xtf
                mapping:
                  oidStrategy: deterministicUuid
                  oidNamespace: cli-bundle
                  basketStrategy: byTopic
                  compileMode: strict
                modeldirs:
                  - MODEL_DIR
                modules:
                  - id: example
                    mapping: ./mapping.yaml
                """.replace("SOURCE_PATH", inputPath.toString().replace("\\", "\\\\"))
                        .replace("MODEL_DIR", modelDir.toString().replace("\\", "\\\\")),
                StandardCharsets.UTF_8);

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "run-bundle",
                        "--manifest",
                        manifestPath.toString(),
                        "--report-dir",
                        reportDir.toString(),
                        "--output",
                        outputPath.toString(),
                        "--no-validate");

        assertThat(exitCode).isZero();
        assertThat(outputPath).exists();

        Map<String, Object> report = new ObjectMapper()
                .readValue(reportDir.resolve("transformation-report.json").toFile(), new TypeReference<>() {});
        assertThat((List<?>) report.getOrDefault("validations", List.of())).isEmpty();
    }

    private static void assertValidateOption(Boolean expected, String... args) throws Exception {
        RunBundleCommand command = new RunBundleCommand();
        new CommandLine(command).parseArgs(args);

        Field field = RunBundleCommand.class.getDeclaredField("validate");
        field.setAccessible(true);
        assertThat((Boolean) field.get(command)).isEqualTo(expected);
    }

    private static void writeP5Input(Path inputPath) throws Exception {
        var compileResult =
                new IliModelService().compileModel("src/test/data/models/p5-test.ili", "src/test/data/models");
        assertThat(compileResult.hasErrors())
                .as("Model compilation errors: %s", compileResult.diagnostics().all())
                .isFalse();

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        IoxWriter writer =
                new InterlisIoFactory().createWriter(inputPath, compileResult.transferDescription(), diagnostics);
        try {
            writer.write(new StartTransferEvent("run-bundle-test", null, null));
            writer.write(new StartBasketEvent("P5Model.P5Topic", "b1"));
            writer.write(new ObjectEvent(source("1", "Alice", "42", "true")));
            writer.write(new ObjectEvent(source("2", "Bob", "7", "false")));
            writer.write(new EndBasketEvent());
            writer.write(new EndTransferEvent());
            writer.flush();
        } finally {
            writer.close();
        }
        assertThat(diagnostics.hasErrors()).isFalse();
    }

    private static Iom_jObject source(String oid, String name, String count, String active) {
        Iom_jObject object = new Iom_jObject("P5Model.P5Topic.SourceClass", oid);
        object.setattrvalue("Name", name);
        object.setattrvalue("Anzahl", count);
        object.setattrvalue("Aktiv", active);
        return object;
    }
}
