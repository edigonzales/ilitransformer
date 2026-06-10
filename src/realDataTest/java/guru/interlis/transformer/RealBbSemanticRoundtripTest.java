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
import guru.interlis.transformer.diag.Severity;
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
import static org.assertj.core.api.Assertions.fail;

@Tag("real-data")
class RealBbSemanticRoundtripTest {

    private static final String MODEL_DIR = "src/test/data/av/models/";
    private static final String DM01_MODEL = "DM01AVCH24LV95D";
    private static final String DMAV_MODEL = "DMAV_Bodenbedeckung_V1_1";
    private static final Path DM01_TO_DMAV_PROFILE = Path.of("profiles/dm01-to-dmav/1.1/bb.yaml");
    private static final Path DM01_INPUT = Path.of("src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf");

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

        IliModelCompileResult dmavResult = modelService.compileModel(DMAV_MODEL,
                MODEL_DIR + ";https://models.interlis.ch");
        if (dmavResult.hasErrors()) {
            fail("DMAV model compilation errors:\n  " + diagnostics(dmavResult));
        }
        dmavTd = dmavResult.transferDescription();
    }

    @Test
    void dm01ToDmavProducesBbObjects() throws Exception {
        Path dmavOutput = tempDir.resolve("dm01-to-dmav-bb.xtf");

        Path mappingPath = materializeDm01ToDmav(DM01_INPUT, dmavOutput);
        run(mappingPath, tempDir.resolve("reports-dm01-forward"));

        List<IomObject> objects = readObjects(dmavOutput, dmavTd);
        assertThat(countBySuffix(objects, ".BBNachfuehrung")).isPositive();
        assertThat(countByTagContains(objects, "Bodenbedeckung.Bodenbedeckung")).isPositive();
        assertThat(countBySuffix(objects, ".Messpunkt")).isPositive();
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add("https://models.interlis.ch");
        DiagnosticCollector diagnostics = new JobRunner().run(mappingPath,
                new RunOptions(modelDirs, false, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-bb-test.yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"",
                        "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-bb.xtf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private List<IomObject> readObjects(Path path, TransferDescription td) throws Exception {
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxReader reader = ioFactory.createReader(path, td);
        List<IomObject> objects = new ArrayList<>();
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    objects.add(objectEvent.getIomObject());
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private long countBySuffix(List<IomObject> objects, String suffix) {
        return objects.stream().filter(obj -> hasSuffix(obj, suffix)).count();
    }

    private long countByTagContains(List<IomObject> objects, String fragment) {
        return objects.stream().filter(obj -> obj.getobjecttag() != null && obj.getobjecttag().contains(fragment)).count();
    }

    private boolean hasSuffix(IomObject obj, String suffix) {
        return obj.getobjecttag() != null && obj.getobjecttag().endsWith(suffix);
    }

    private static String diagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .collect(java.util.stream.Collectors.joining("\n  "));
    }
}
