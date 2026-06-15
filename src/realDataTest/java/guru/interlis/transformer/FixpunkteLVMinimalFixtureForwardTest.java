package guru.interlis.transformer;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("real-data")
class FixpunkteLVMinimalFixtureForwardTest {

    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String DM01_MODEL = Dm01DmavPaths.DM01_MODEL;
    private static final String DMAV_MODEL = Dm01DmavPaths.DMAV_FIXPUNKTELV_MODEL;
    private static final Path PROFILE = Dm01DmavFixtures.FIXPUNKTELV.dm01ToDmavProfile();
    private static final Path DM01_INPUT = Dm01DmavFixtures.FIXPUNKTELV.dm01MinimalFixture();

    private static TransferDescription dm01Td;
    private static TransferDescription dmavTd;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void compileModels() {
        IliModelService modelService = new IliModelService();

        IliModelCompileResult dm01Result = modelService.compileModel(DM01_MODEL, MODEL_DIR);
        if (dm01Result.hasErrors()) {
            fail("DM01 model compilation errors:\n  " + diagnostics(dm01Result));
        }
        dm01Td = dm01Result.transferDescription();

        IliModelCompileResult dmavResult = modelService.compileModel(
                DMAV_MODEL, Dm01DmavPaths.LOCAL_AND_REMOTE_MODEL_DIRS);
        if (dmavResult.hasErrors()) {
            fail("DMAV model compilation errors:\n  " + diagnostics(dmavResult));
        }
        dmavTd = dmavResult.transferDescription();
    }

    @Test
    void modelsCompile() {
        assertThat(dm01Td).isNotNull();
        assertThat(dmavTd).isNotNull();
    }

    @Test
    void forwardTransformationProducesValidOutput() throws Exception {
        Path outputPath = tempDir.resolve("dmav-fixpunktelv-output.xtf");
        Path mappingPath = materializeProfile(outputPath);

        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        RunOptions options = new RunOptions(modelDirs, true, tempDir, false);
        JobRunner runner = new JobRunner();
        DiagnosticCollector diagnostics = runner.run(mappingPath, options);

        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                .toList();
        assertThat(errors).as("Transformation diagnostics: %s", diagnostics.all()).isEmpty();
        assertThat(outputPath).exists();

        String content = Files.readString(outputPath, StandardCharsets.UTF_8);
        assertThat(content).contains("LFP1");
        assertThat(content).contains("HFP1");
        assertThat(content).contains("Geometrie");
        assertThat(content).contains("Lagegenauigkeit");
    }

    @Test
    void outputCanBeReRead() throws Exception {
        Path outputPath = tempDir.resolve("dmav-fixpunktelv-reread.xtf");
        Path mappingPath = materializeProfile(outputPath);

        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);

        RunOptions options = new RunOptions(modelDirs, false, tempDir, false);
        JobRunner runner = new JobRunner();
        DiagnosticCollector diagnostics = runner.run(mappingPath, options);

        assertThat(diagnostics.hasErrors()).isFalse();
        assertThat(outputPath).exists();

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxReader reader = ioFactory.createReader(outputPath, dmavTd);

        int lfp1Count = 0;
        int hfp1Count = 0;
        IoxEvent event;
        while ((event = reader.read()) != null) {
            if (event instanceof ObjectEvent objEvent) {
                IomObject obj = objEvent.getIomObject();
                String tag = obj.getobjecttag();
                if (tag != null && tag.contains(".LFP1")) {
                    lfp1Count++;
                } else if (tag != null && tag.contains(".HFP1")) {
                    hfp1Count++;
                }
            }
        }
        reader.close();

        assertThat(lfp1Count).isEqualTo(1);
        assertThat(hfp1Count).isEqualTo(1);
    }

    private Path materializeProfile(Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve(outputPath.getFileName() + "-mapping.yaml");
        String yaml = Files.readString(PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"",
                        "path: \"" + DM01_INPUT.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-fixpunktelv.xtf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private static String diagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .collect(java.util.stream.Collectors.joining("\n  "));
    }
}
