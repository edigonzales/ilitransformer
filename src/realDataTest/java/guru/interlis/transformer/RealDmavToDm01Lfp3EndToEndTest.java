package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.ModelRegistry;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("real-data")
class RealDmavToDm01Lfp3EndToEndTest {

    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String DM01_MODEL = Dm01DmavPaths.DM01_MODEL;
    private static final String DMAV_MODEL = Dm01DmavPaths.DMAV_LFP3_MODEL;
    private static final Path PROFILE = Dm01DmavFixtures.LFP3.dmavToDm01Profile();
    private static final Path DMAV_INPUT = Dm01DmavFixtures.LFP3.dmavRealExtractFixture();

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
    void compilesProductiveProfile() throws Exception {
        MappingLoader loader = new MappingLoader();
        JobConfig config = loader.load(PROFILE);

        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .modelDirs(config.job.modeldir)
                .modelDirs(modelDirs)
                .baseDirectory(PROFILE.toAbsolutePath().getParent())
                .build();

        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(3);
        assertThat(plan.enumMaps()).containsKeys("Zuverlaessigkeit_DMAV_DM01", "Versicherungsart_DMAV_DM01");
        assertThat(plan.rules().stream()
                        .filter(rule -> "lfp3".equals(rule.ruleId()))
                        .flatMap(rule -> rule.losses().stream()))
                .hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void fullTransformationProducesValidDm01Output() throws Exception {
        Path outputPath = tempDir.resolve("dm01-lfp3-real.itf");
        Path mappingPath = materializeProfile(outputPath);

        DiagnosticCollector diagnostics =
                run(mappingPath, true, Path.of("build/reports/realDataTest/dmav-to-dm01-lfp3-validation"));
        assertNoErrors(diagnostics);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath, StandardCharsets.ISO_8859_1);
        assertThat(content).contains("TOPI FixpunkteKategorie3");
        assertThat(content).contains("TABL LFP3Nachfuehrung");
        assertThat(content).contains("TABL LFP3");
        assertThat(content).contains("TABL LFP3Pos");
        assertThat(content).contains("TABL LFP3Symbol");
    }

    @Test
    void lossinessReportContainsDmavOnlyFields() throws Exception {
        Path outputPath = tempDir.resolve("dm01-lfp3-loss.itf");
        Path reportDir = tempDir.resolve("reports-loss");
        Path mappingPath = materializeProfile(outputPath);

        DiagnosticCollector diagnostics = run(mappingPath, false, reportDir);
        assertNoErrors(diagnostics);

        Path json = reportDir.resolve("lossiness-report.json");
        Path markdown = reportDir.resolve("lossiness-report.md");
        assertThat(json).exists();
        assertThat(markdown).exists();

        String jsonText = Files.readString(json, StandardCharsets.UTF_8);
        assertThat(jsonText).contains("DMAV_ONLY");
        assertThat(jsonText).contains("p.LFPArt");
        assertThat(jsonText).contains("p.AktiverUnterhalt");
    }

    @Test
    void deterministicOutputAcrossTwoRuns() throws Exception {
        Path output1 = tempDir.resolve("run1.itf");
        Path output2 = tempDir.resolve("run2.itf");

        DiagnosticCollector diag1 = run(materializeProfile(output1), false, tempDir.resolve("reports1"));
        assertNoErrors(diag1);

        DiagnosticCollector diag2 = run(materializeProfile(output2), false, tempDir.resolve("reports2"));
        assertNoErrors(diag2);

        assertThat(output1).exists();
        assertThat(output2).exists();
        assertThat(Files.readString(output1, StandardCharsets.ISO_8859_1))
                .isEqualTo(Files.readString(output2, StandardCharsets.ISO_8859_1));
    }

    @Test
    void outputCanBeReRead() throws Exception {
        Path outputPath = tempDir.resolve("dm01-lfp3-reread.itf");
        DiagnosticCollector diagnostics = run(materializeProfile(outputPath), false, tempDir.resolve("reports-reread"));
        assertNoErrors(diagnostics);

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxReader reader = ioFactory.createReader(outputPath, dm01Td);

        int lfp3Count = 0;
        int lfp3nfCount = 0;
        int lfp3posCount = 0;
        int lfp3symbolCount = 0;
        ch.interlis.iox.IoxEvent event;
        while ((event = reader.read()) != null) {
            if (event instanceof ch.interlis.iox_j.ObjectEvent objEvent) {
                IomObject obj = objEvent.getIomObject();
                String tag = obj.getobjecttag();
                if (tag.endsWith(".LFP3Nachfuehrung")) {
                    lfp3nfCount++;
                } else if (tag.endsWith(".LFP3")) {
                    lfp3Count++;
                } else if (tag.endsWith(".LFP3Pos")) {
                    lfp3posCount++;
                } else if (tag.endsWith(".LFP3Symbol")) {
                    lfp3symbolCount++;
                }
            }
        }
        reader.close();

        assertThat(lfp3nfCount).isEqualTo(3);
        assertThat(lfp3Count).isEqualTo(113);
        assertThat(lfp3posCount).isEqualTo(113);
        assertThat(lfp3symbolCount).isEqualTo(113);
    }

    private DiagnosticCollector run(Path mappingPath, boolean validateOutput, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);
        RunOptions options = new RunOptions(modelDirs, validateOutput, reportDir, false);
        return new JobRunner().run(mappingPath, options);
    }

    private Path materializeProfile(Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-lfp3-real.yaml");
        String yaml = Files.readString(PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"", "path: \"" + DMAV_INPUT.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-lfp3.itf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private static void assertNoErrors(DiagnosticCollector diagnostics) {
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }
}
