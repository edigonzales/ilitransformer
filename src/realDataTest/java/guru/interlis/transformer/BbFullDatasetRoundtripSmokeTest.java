package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;

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
class BbFullDatasetRoundtripSmokeTest {

    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String DM01_MODEL = Dm01DmavPaths.DM01_MODEL;
    private static final String DMAV_MODEL = Dm01DmavPaths.DMAV_BB_MODEL;
    private static final Path DM01_TO_DMAV_PROFILE = Dm01DmavFixtures.BB.dm01ToDmavProfile();
    private static final Path DMAV_TO_DM01_PROFILE = Dm01DmavFixtures.BB.dmavToDm01Profile();
    private static final Path DM01_INPUT = Dm01DmavPaths.FULL_DM01_DATASET;

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

        IliModelCompileResult dmavResult =
                modelService.compileModel(DMAV_MODEL, MODEL_DIR + ";https://models.interlis.ch");
        if (dmavResult.hasErrors()) {
            fail("DMAV model compilation errors:\n  " + diagnostics(dmavResult));
        }
        dmavTd = dmavResult.transferDescription();
    }

    @Test
    void dm01ToDmavToDm01KeepsBbSemantics() throws Exception {
        Path dmavIntermediate = tempDir.resolve("dm01-to-dmav-bb.xtf");
        Path dm01Roundtrip = tempDir.resolve("dm01-roundtrip-bb.itf");

        run(materializeDm01ToDmav(DM01_INPUT, dmavIntermediate), tempDir.resolve("reports-dm01-forward"));
        run(materializeDmavToDm01(dmavIntermediate, dm01Roundtrip), tempDir.resolve("reports-dmav-reverse"));

        assertThat(dm01Roundtrip).exists();
        assertThat(Files.size(dm01Roundtrip)).isGreaterThan(0);

        List<IomObject> objects = readObjects(dm01Roundtrip, dm01Td);
        assertThat(countBySuffix(objects, ".BoFlaeche")).isPositive();
        assertThat(countBySuffix(objects, ".ProjBoFlaeche")).isPositive();
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add("https://models.interlis.ch");
        DiagnosticCollector diagnostics =
                new JobRunner().run(mappingPath, new RunOptions(modelDirs, false, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        List<Diagnostic> warnings = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.WARNING)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
        List<Diagnostic> unexpectedWarnings = warnings.stream()
                .filter(warning -> !isExpectedBbStatusLookupWarning(warning))
                .toList();
        assertThat(unexpectedWarnings).as("Warnings: %s", diagnostics.all()).isEmpty();
    }

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-bb-test.yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-bb.xtf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private Path materializeDmavToDm01(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-bb-test.yaml");
        String yaml = Files.readString(DMAV_TO_DM01_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-bb.itf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
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
        return objects.stream()
                .filter(obj -> obj.getobjecttag() != null && obj.getobjecttag().contains(fragment))
                .count();
    }

    private boolean hasSuffix(IomObject obj, String suffix) {
        return obj.getobjecttag() != null && obj.getobjecttag().endsWith(suffix);
    }

    private boolean isExpectedBbStatusLookupWarning(Diagnostic warning) {
        if (!DiagnosticCode.LOOKUP_NO_MATCH.equals(warning.code())) {
            return false;
        }
        if (!"bb-nachfuehrung-gueltig".equals(warning.sourcePath())
                && !"bb-nachfuehrung-projektiert".equals(warning.sourcePath())) {
            return false;
        }
        return warning.message() != null
                && warning.message().contains("DMAV_Bodenbedeckung_V1_1.Bodenbedeckung.Bodenbedeckung.Entstehung=");
    }

    private static String diagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .collect(java.util.stream.Collectors.joining("\n  "));
    }
}
