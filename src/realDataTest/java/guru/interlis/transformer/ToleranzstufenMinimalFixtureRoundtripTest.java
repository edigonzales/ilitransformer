package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.compare.ComparisonProfile;
import guru.interlis.transformer.compare.ComparisonReport;
import guru.interlis.transformer.compare.SemanticTransferComparator;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("real-data")
class ToleranzstufenMinimalFixtureRoundtripTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String DM01_MODEL = Dm01DmavPaths.DM01_MODEL;
    private static final String DMAV_MODEL = Dm01DmavPaths.DMAV_TOLERANZSTUFEN_MODEL;
    private static final Path DM01_TO_DMAV_PROFILE = Dm01DmavFixtures.TOLERANZSTUFEN.dm01ToDmavProfile();
    private static final Path DMAV_TO_DM01_PROFILE = Dm01DmavFixtures.TOLERANZSTUFEN.dmavToDm01Profile();
    private static final Path DM01_INPUT = Dm01DmavFixtures.TOLERANZSTUFEN.dm01MinimalFixture();
    private static final Path DMAV_INPUT = Dm01DmavFixtures.TOLERANZSTUFEN.dmavMinimalFixture();

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
    void dm01ToDmavToDm01KeepsToleranzstufenSemantics() throws Exception {
        Path dmavIntermediate = tempDir.resolve("dm01-to-dmav.xtf");
        Path dm01Roundtrip = tempDir.resolve("dm01-roundtrip.itf");

        run(materializeDm01ToDmav(DM01_INPUT, dmavIntermediate), tempDir.resolve("reports-dm01-forward"));
        run(materializeDmavToDm01(dmavIntermediate, dm01Roundtrip), tempDir.resolve("reports-dm01-reverse"));

        List<IomObject> original = semanticDm01Objects(DM01_INPUT);
        List<IomObject> roundtripped = semanticDm01Objects(dm01Roundtrip);

        ComparisonReport report =
                new SemanticTransferComparator().compare(original, roundtripped, dm01RoundtripProfile());

        assertThat(report.equivalent())
                .as("Semantic differences: %s", report.errors())
                .isTrue();
    }

    @Test
    void dmavToDm01ToDmavKeepsToleranzstufenSemantics() throws Exception {
        Path dm01Intermediate = tempDir.resolve("dmav-to-dm01.itf");
        Path dmavRoundtrip = tempDir.resolve("dmav-roundtrip.xtf");
        Path reverseReportDir = tempDir.resolve("reports-dmav-reverse");

        run(materializeDmavToDm01(DMAV_INPUT, dm01Intermediate), reverseReportDir);
        run(materializeDm01ToDmav(dm01Intermediate, dmavRoundtrip), tempDir.resolve("reports-dmav-forward"));

        List<IomObject> original = semanticDmavObjects(DMAV_INPUT);
        List<IomObject> roundtripped = semanticDmavObjects(dmavRoundtrip);

        ComparisonReport report =
                new SemanticTransferComparator().compare(original, roundtripped, dmavRoundtripProfile());

        assertThat(report.equivalent())
                .as("Semantic differences: %s", report.errors())
                .isTrue();
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

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-" + outputPath.getFileName() + ".yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace(
                        "path: \"build/out/dmav-toleranzstufen.xtf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private Path materializeDmavToDm01(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-" + outputPath.getFileName() + ".yaml");
        String yaml = Files.readString(DMAV_TO_DM01_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace(
                        "path: \"build/out/dm01-toleranzstufen.itf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private List<IomObject> semanticDm01Objects(Path path) throws Exception {
        return readObjects(path, dm01Td).stream()
                .filter(obj -> hasSuffix(obj, ".Toleranzstufe"))
                .toList();
    }

    private List<IomObject> semanticDmavObjects(Path path) throws Exception {
        return readObjects(path, dmavTd).stream()
                .filter(obj -> hasSuffix(obj, ".Toleranzstufe"))
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
                .businessKey("Toleranzstufe", "NBIdent", "Identifikator")
                .numericTolerance(100.0)
                .ignore("Entstehung")
                .ignore("ToleranzstufePos")
                .build();
    }

    private ComparisonProfile dmavRoundtripProfile() {
        return ComparisonProfile.builder()
                .businessKey("Toleranzstufe", "NBIdent", "Identifikator")
                .numericTolerance(0.001)
                .ignore("Entstehung")
                .ignore("TSNachfuehrung")
                .build();
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
