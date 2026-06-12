package guru.interlis.transformer;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxReader;
import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.ModelRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Tag("real-data")
class RealDm01ToDmavLfp3EndToEndTest {

    private static final String MODEL_DIR = "src/test/data/av/models/";
    private static final String DM01_MODEL = "DM01AVCH24LV95D";
    private static final String DMAV_MODEL = "DMAV_FixpunkteAVKategorie3_V1_1";
    private static final Path PROFILE = Path.of("profiles/dm01-to-dmav/1.1/lfp3.yaml");
    private static final Path DM01_INPUT = Path.of("src/test/resources/real-dm01-dmav/lfp3/dm01-input.itf");

    private static IliModelService modelService;
    private static TransferDescription dm01Td;
    private static TransferDescription dmavTd;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void compileModels() {
        modelService = new IliModelService();
        List<String> modelDirs = Dm01DmavPaths.defaultModelDirs();

        IliModelCompileResult dm01Result = modelService.compileModel(DM01_MODEL, MODEL_DIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DM01 model compilation errors:\n  " + errors);
        }
        dm01Td = dm01Result.transferDescription();

        IliModelCompileResult dmavResult = modelService.compileModel(DMAV_MODEL, String.join(";", modelDirs));
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DMAV model compilation errors:\n  " + errors);
        }
        dmavTd = dmavResult.transferDescription();
    }

    @Test
    void dm01ModelCompiles() {
        assertThat(dm01Td).isNotNull();
        assertThat(dm01Td.iterator()).hasNext();
    }

    @Test
    void dmavModelCompiles() {
        assertThat(dmavTd).isNotNull();
        assertThat(dmavTd.iterator()).hasNext();
    }

    @Test
    void compilesProductiveMapping() throws Exception {
        MappingLoader loader = new MappingLoader();
        JobConfig config = loader.load(PROFILE);

        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .modelDirs(config.job.modeldir)
                .modelDirs(modelDirs)
                .baseDirectory(PROFILE.toAbsolutePath().getParent())
                .build();

        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        if (plan.diagnostics().hasErrors()) {
            System.out.println("=== Compiler Errors ===");
            for (Diagnostic d : plan.diagnostics().all()) {
                System.out.printf("[%s] %s: %s%n", d.severity(), d.code(), d.message());
            }
        }
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        assertThat(plan.rules()).hasSize(2);
        assertThat(plan.enumMaps()).containsKeys("Zuverlaessigkeit_DM01_DMAV", "Versicherungsart_DM01_DMAV");
    }

    @Test
    void fullTransformationProducesValidOutput() throws Exception {
        Path outputPath = tempDir.resolve("dmav-lfp3-output.xtf");
        Path mappingPath = materializeProfile(outputPath);

        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        RunOptions options = new RunOptions(modelDirs, true, tempDir, false);

        JobRunner runner = new JobRunner();
        DiagnosticCollector diagnostics = runner.run(mappingPath, options);

        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                .toList();

        if (!errors.isEmpty()) {
            System.out.println("=== Transformation Errors ===");
            for (Diagnostic d : errors) {
                System.out.printf("[%s] %s: %s%n", d.severity(), d.code(), d.message());
            }
        }

        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == guru.interlis.transformer.diag.Severity.WARNING) {
                System.out.printf("[WARN] %s: %s%n", d.code(), d.message());
            }
        }

        assertThat(errors).as("Transformation should produce no errors").isEmpty();

        // Verify output was committed (not rolled back)
        if (Files.exists(outputPath)) {
            String content = Files.readString(outputPath, StandardCharsets.UTF_8);
            System.out.println("Output size: " + content.length() + " chars");
            assertThat(content).contains("LFP3Nachfuehrung");
            assertThat(content).contains("LFP3");
            assertThat(content).contains("Entstehung");
            assertThat(content).contains("LFPArt>LFP3<");
        }
    }

    @Test
    void outputValidatesWithIlivalidator() throws Exception {
        Path mappingPath = materializeProfile(tempDir.resolve("dmav-lfp3-output-validate.xtf"));

        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        RunOptions options = new RunOptions(modelDirs, true, buildDir(tempDir), false);

        JobRunner runner = new JobRunner();
        DiagnosticCollector diagnostics = runner.run(mappingPath, options);

        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                .toList();
        assertThat(errors).as("Transformation should produce no errors").isEmpty();

        // Check validation results in diagnostics
        boolean validationPassed = diagnostics.all().stream()
                .noneMatch(d -> "VALIDATION_FAILED".equals(d.code()));
        assertThat(validationPassed).as("Output should pass ilivalidator validation").isTrue();
    }

    @Test
    void deterministicOidsAcrossTwoRuns() throws Exception {
        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        Path output1 = tempDir.resolve("run1.xtf");
        Path output2 = tempDir.resolve("run2.xtf");

        JobRunner runner1 = new JobRunner();
        RunOptions opts1 = new RunOptions(modelDirs, false, tempDir, false);
        DiagnosticCollector diag1 = runner1.run(materializeProfile(output1), opts1);

        assertThat(diag1.hasErrors()).isFalse();

        // Second run
        JobRunner runner2 = new JobRunner();
        RunOptions opts2 = new RunOptions(modelDirs, false, tempDir, false);
        DiagnosticCollector diag2 = runner2.run(materializeProfile(output2), opts2);

        assertThat(diag2.hasErrors()).isFalse();

        // Both outputs should exist and be equivalent
        assertThat(output1).exists();
        assertThat(output2).exists();

        String content1 = Files.readString(output1, StandardCharsets.UTF_8);
        String content2 = Files.readString(output2, StandardCharsets.UTF_8);

        // Extract all TID values from both runs
        List<String> tids1 = extractTids(content1);
        List<String> tids2 = extractTids(content2);

        System.out.println("Run 1 TIDs: " + tids1.size());
        System.out.println("Run 2 TIDs: " + tids2.size());

        assertThat(tids1).isEqualTo(tids2);
    }

    @Test
    void outputContainsExpectedAttributes() throws Exception {
        Path outputPath = tempDir.resolve("dmav-lfp3-attrs.xtf");
        Path mappingPath = materializeProfile(outputPath);

        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        JobRunner runner = new JobRunner();
        RunOptions options = new RunOptions(modelDirs, true, tempDir, false);
        DiagnosticCollector diagnostics = runner.run(mappingPath, options);

        assertThat(diagnostics.hasErrors()).isFalse();

        assertThat(outputPath).exists();

        String content = Files.readString(outputPath, StandardCharsets.UTF_8);

        assertThat(content).contains("NBIdent");
        assertThat(content).contains("Identifikator");
        assertThat(content).contains("Beschreibung");
        assertThat(content).contains("LFPArt");
        assertThat(content).contains("Geometrie");
        assertThat(content).contains("Hoehengeometrie");
        assertThat(content).contains("Lagegenauigkeit");
        assertThat(content).contains("IstLagezuverlaessig");
        assertThat(content).contains("Hoehengenauigkeit");
        assertThat(content).contains("IstHoehenzuverlaessig");
        assertThat(content).contains("Punktzeichen");
        assertThat(content).contains("Grenzpunktfunktion");
        assertThat(content).contains("AktiverUnterhalt");
        assertThat(content).contains("Entstehung");
        assertThat(content).contains("Textposition");
    }

    @Test
    void outputCanBeReRead() throws Exception {
        Path outputPath = tempDir.resolve("dmav-lfp3-reread.xtf");
        Path mappingPath = materializeProfile(outputPath);

        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        JobRunner runner = new JobRunner();
        RunOptions options = new RunOptions(modelDirs, false, tempDir, false);
        DiagnosticCollector diagnostics = runner.run(mappingPath, options);

        assertThat(diagnostics.hasErrors()).isFalse();

        assertThat(outputPath).exists();

        // Read output back using XTF reader
        guru.interlis.transformer.interlis.InterlisIoFactory ioFactory =
                new guru.interlis.transformer.interlis.InterlisIoFactory();
        IoxReader reader = ioFactory.createReader(outputPath, dmavTd);

        int lfp3Count = 0;
        int lfp3nfCount = 0;
        ch.interlis.iox.IoxEvent event;
        while ((event = reader.read()) != null) {
            if (event instanceof ch.interlis.iox_j.ObjectEvent objEvent) {
                IomObject obj = objEvent.getIomObject();
                String tag = obj.getobjecttag();
                if (tag.contains("LFP3Nachfuehrung") && !tag.contains("HFP3")) {
                    lfp3nfCount++;
                } else if (tag.contains("LFP3") && !tag.contains("Nachfuehrung")
                        && !tag.contains("HFP3") && !tag.contains("Pos") && !tag.contains("Symbol")) {
                    lfp3Count++;
                }
            }
        }
        reader.close();

        System.out.println("Re-read: LFP3Nachfuehrung=" + lfp3nfCount + " LFP3=" + lfp3Count);
        assertThat(lfp3Count).isGreaterThan(0);
        assertThat(lfp3nfCount).isGreaterThan(0);
    }

    private static List<String> extractTids(String xml) {
        List<String> tids = new ArrayList<>();
        int idx = 0;
        while ((idx = xml.indexOf("ili:tid=\"", idx)) != -1) {
            idx += 9;
            int end = xml.indexOf("\"", idx);
            if (end > idx) {
                tids.add(xml.substring(idx, end));
                idx = end + 1;
            } else {
                break;
            }
        }
        return tids;
    }

    private static Path buildDir(Path tempDir) {
        return tempDir.resolve("build/out");
    }

    private Path materializeProfile(Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve(outputPath.getFileName() + "-mapping.yaml");
        String yaml = Files.readString(PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"",
                        "path: \"" + DM01_INPUT.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-lfp3.xtf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }
}
