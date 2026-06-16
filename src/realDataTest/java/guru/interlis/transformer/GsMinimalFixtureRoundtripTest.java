package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.compare.ComparisonProfile;
import guru.interlis.transformer.diag.Diagnostic;
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
class GsMinimalFixtureRoundtripTest {

    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String DM01_MODEL = Dm01DmavPaths.DM01_MODEL;
    private static final String DMAV_MODEL = Dm01DmavPaths.DMAV_GS_MODEL;
    private static final Path DM01_TO_DMAV_PROFILE = Dm01DmavFixtures.GS.dm01ToDmavProfile();
    private static final Path DMAV_TO_DM01_PROFILE = Dm01DmavFixtures.GS.dmavToDm01Profile();
    private static final Path DM01_INPUT = Dm01DmavFixtures.GS.dm01MinimalFixture();
    private static final Path DMAV_INPUT = Dm01DmavFixtures.GS.dmavMinimalFixture();

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
                modelService.compileModel(DMAV_MODEL, Dm01DmavPaths.LOCAL_AND_REMOTE_MODEL_DIRS);
        if (dmavResult.hasErrors()) {
            fail("DMAV model compilation errors:\n  " + diagnostics(dmavResult));
        }
        dmavTd = dmavResult.transferDescription();
    }

    @Test
    void dm01ToDmavToDm01KeepsCoreGsSemantics() throws Exception {
        Path dmavIntermediate = tempDir.resolve("dm01-to-dmav.xtf");
        Path dm01Roundtrip = tempDir.resolve("dm01-roundtrip.itf");

        runWithoutValidation(
                materializeDm01ToDmav(DM01_INPUT, dmavIntermediate), tempDir.resolve("reports-dm01-forward"));
        runWithoutValidation(
                materializeDmavToDm01(dmavIntermediate, dm01Roundtrip), tempDir.resolve("reports-dm01-reverse"));

        assertThat(dm01Roundtrip).exists();
        String content = Files.readString(dm01Roundtrip, StandardCharsets.ISO_8859_1);
        assertThat(content).contains("TOPI Liegenschaften");
        assertThat(content).contains("TABL LSNachfuehrung");
        assertThat(content).contains("TABL Grenzpunkt");
        assertThat(content).contains("TABL Grundstueck");
        assertThat(content).contains("TABL Liegenschaft");
        assertThat(content).contains("TABL SelbstRecht");
        assertThat(content).contains("TABL ProjGrundstueck");
        assertThat(content).contains("TABL ProjLiegenschaft");
        assertThat(content).contains("TOPI Gemeindegrenzen");
        assertThat(content).contains("TABL Hoheitsgrenzpunkt");
        assertThat(content).contains("GSNB");
        assertThat(content).contains("GP001");
        assertThat(content).contains("HGP001");
        assertThat(content).contains("HGP003");

        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".Grenzpunkt"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".Grenzpunkt"));
        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".Hoheitsgrenzpunkt"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".Hoheitsgrenzpunkt"));
        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".Grundstueck"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".Grundstueck"));
        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".Liegenschaft"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".Liegenschaft"));
        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".ProjGrundstueck"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".ProjGrundstueck"));
        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".ProjLiegenschaft"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".ProjLiegenschaft"));
        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".SelbstRecht"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".SelbstRecht"));
    }

    @Test
    void dmavToDm01ToDmavKeepsCoreGsSemanticsWithExpectedLosses() throws Exception {
        Path dm01Intermediate = tempDir.resolve("dmav-to-dm01.itf");
        Path dmavRoundtrip = tempDir.resolve("dmav-roundtrip.xtf");

        runWithoutValidation(
                materializeDmavToDm01(DMAV_INPUT, dm01Intermediate), tempDir.resolve("reports-dmav-reverse"));
        runWithoutValidation(
                materializeDm01ToDmav(dm01Intermediate, dmavRoundtrip), tempDir.resolve("reports-dmav-forward"));

        assertThat(dmavRoundtrip).exists();
        String content = Files.readString(dmavRoundtrip, StandardCharsets.UTF_8);
        assertThat(content).contains("GSNachfuehrung");
        assertThat(content).contains("Grenzpunkt");
        assertThat(content).contains("IstHoheitsgrenzpunkt>true<");
        assertThat(content).contains("Nummer>GP001<");
        assertThat(content).contains("Nummer>HGP001<");
        assertThat(content).contains("Grundstueck");
        assertThat(content).contains("Liegenschaft");
        assertThat(content).contains("SelbstaendigesDauerndesRecht");
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);
        DiagnosticCollector diagnostics =
                new JobRunner().run(mappingPath, new RunOptions(modelDirs, true, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private void runWithoutValidation(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);
        DiagnosticCollector diagnostics =
                new JobRunner().run(mappingPath, new RunOptions(modelDirs, false, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-" + outputPath.getFileName() + ".yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-gs.xtf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private Path materializeDmavToDm01(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-" + outputPath.getFileName() + ".yaml");
        String yaml = Files.readString(DMAV_TO_DM01_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-gs.itf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private List<IomObject> semanticDm01Objects(Path path) throws Exception {
        return readObjects(path, dm01Td).stream()
                .filter(obj -> hasSuffix(obj, ".LSNachfuehrung")
                        || hasSuffix(obj, ".Grenzpunkt")
                        || hasSuffix(obj, ".Hoheitsgrenzpunkt")
                        || hasSuffix(obj, ".ProjGrundstueck")
                        || hasSuffix(obj, ".Grundstueck")
                        || hasSuffix(obj, ".ProjLiegenschaft")
                        || hasSuffix(obj, ".Liegenschaft")
                        || hasSuffix(obj, ".SelbstRecht"))
                .toList();
    }

    private List<IomObject> semanticDmavObjects(Path path) throws Exception {
        return readObjects(path, dmavTd).stream()
                .filter(obj -> hasSuffix(obj, ".GSNachfuehrung")
                        || hasSuffix(obj, ".Grenzpunkt")
                        || hasSuffix(obj, ".Grundstueck")
                        || hasSuffix(obj, ".Liegenschaft")
                        || hasSuffix(obj, ".SelbstaendigesDauerndesRecht"))
                .filter(obj -> !(hasSuffix(obj, ".Grenzpunkt") && hasAttr(obj, "IstHoheitsgrenzpunkt", "true")))
                .toList();
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

    private ComparisonProfile dm01RoundtripProfile() {
        return ComparisonProfile.builder()
                .businessKey("LSNachfuehrung", "NBIdent", "Identifikator")
                .businessKey("Grenzpunkt", "Identifikator", "Geometrie")
                .businessKey("Hoheitsgrenzpunkt", "Identifikator", "Geometrie")
                .businessKey("ProjGrundstueck", "NBIdent", "Nummer")
                .businessKey("Grundstueck", "NBIdent", "Nummer")
                .businessKey("ProjLiegenschaft", "Flaechenmass", "Geometrie")
                .businessKey("Liegenschaft", "Flaechenmass", "Geometrie")
                .businessKey("SelbstRecht", "Flaechenmass", "Geometrie")
                .numericTolerance(0.001)
                .ignore("Entstehung")
                .ignore("GrenzpunktPos")
                .ignore("GrenzpunktSymbol")
                .ignore("HoheitsgrenzpunktPos")
                .ignore("HoheitsgrenzpunktSymbol")
                .ignore("GrundstueckPos")
                .ignore("ProjGrundstueckPos")
                .ignore("Liegenschaft_von")
                .ignore("ProjLiegenschaft_von")
                .ignore("SelbstRecht_von")
                .build();
    }

    private ComparisonProfile dmavRoundtripProfile() {
        return ComparisonProfile.builder()
                .businessKey("GSNachfuehrung", "NBIdent", "Identifikator")
                .businessKey("Grenzpunkt", "Nummer")
                .businessKey("Grundstueck", "NBIdent", "Nummer")
                .businessKey("Liegenschaft", "Flaechenmass")
                .businessKey("SelbstaendigesDauerndesRecht", "Flaechenmass")
                .numericTolerance(0.001)
                .ignore("Entstehung")
                .ignore("Untergang")
                .ignore("Grundstueck")
                .ignore("NBIdent")
                .ignore("SymbolOri")
                .ignore("Textposition")
                .ignore("Fiktiv")
                .ignore("Hoehengeometrie")
                .ignore("Hoehengenauigkeit")
                .ignore("IstHoehenzuverlaessig")
                .ignore("Grundbucheintrag")
                .ignore("Qualitaetsstandard")
                .ignore("IstBaurecht")
                .build();
    }

    private long countBySuffix(List<IomObject> objects, String suffix) {
        return objects.stream().filter(obj -> hasSuffix(obj, suffix)).count();
    }

    private boolean hasSuffix(IomObject obj, String suffix) {
        return obj.getobjecttag() != null && obj.getobjecttag().endsWith(suffix);
    }

    private boolean hasAttr(IomObject obj, String attrName, String value) {
        String v = obj.getattrvalue(attrName);
        return v != null && v.equals(value);
    }

    private static String diagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .collect(java.util.stream.Collectors.joining("\n  "));
    }
}
