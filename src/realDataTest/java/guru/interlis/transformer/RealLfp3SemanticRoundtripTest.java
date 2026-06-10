package guru.interlis.transformer;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.compare.ComparisonProfile;
import guru.interlis.transformer.compare.ComparisonReport;
import guru.interlis.transformer.compare.SemanticTransferComparator;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.loss.LossEvent;
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
class RealLfp3SemanticRoundtripTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL_DIR = "src/test/data/av/models/";
    private static final String DM01_MODEL = "DM01AVCH24LV95D";
    private static final String DMAV_MODEL = "DMAV_FixpunkteAVKategorie3_V1_1";
    private static final Path DM01_TO_DMAV_PROFILE = Path.of("profiles/dm01-to-dmav/1.1/lfp3.yaml");
    private static final Path DMAV_TO_DM01_PROFILE = Path.of("profiles/dmav-to-dm01/1.1/lfp3.yaml");
    private static final Path DM01_INPUT = Path.of("src/test/resources/real-dm01-dmav/lfp3/dm01-input.itf");
    private static final Path DMAV_INPUT = Path.of("src/test/resources/real-dm01-dmav/lfp3/dmav-input.xtf");

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
    void dm01ToDmavToDm01KeepsLfp3Semantics() throws Exception {
        Path dmavIntermediate = tempDir.resolve("dm01-to-dmav.xtf");
        Path dm01Roundtrip = tempDir.resolve("dm01-roundtrip.itf");

        run(materializeDm01ToDmav(DM01_INPUT, dmavIntermediate),
                tempDir.resolve("reports-dm01-forward"));
        run(materializeDmavToDm01(dmavIntermediate, dm01Roundtrip),
                tempDir.resolve("reports-dm01-reverse"));

        List<IomObject> original = semanticDm01Objects(DM01_INPUT);
        List<IomObject> roundtripped = semanticDm01Objects(dm01Roundtrip);

        ComparisonProfile profile = dm01RoundtripProfile();
        ComparisonReport report = new SemanticTransferComparator()
                .compare(original, roundtripped, profile);

        assertThat(report.equivalent())
                .as("Semantic differences: %s", report.errors())
                .isTrue();
        assertThat(countBySuffix(readObjects(DM01_INPUT, dm01Td), ".LFP3Pos"))
                .isEqualTo(countBySuffix(readObjects(dm01Roundtrip, dm01Td), ".LFP3Pos"));
    }

    @Test
    void dmavToDm01ToDmavKeepsLfp3SemanticsWithExpectedLosses() throws Exception {
        Path dm01Intermediate = tempDir.resolve("dmav-to-dm01.itf");
        Path dmavRoundtrip = tempDir.resolve("dmav-roundtrip.xtf");
        Path reverseReportDir = tempDir.resolve("reports-dmav-reverse");

        run(materializeDmavToDm01(DMAV_INPUT, dm01Intermediate), reverseReportDir);
        run(materializeDm01ToDmav(dm01Intermediate, dmavRoundtrip),
                tempDir.resolve("reports-dmav-forward"));

        List<IomObject> original = semanticDmavObjects(DMAV_INPUT);
        List<IomObject> roundtripped = semanticDmavObjects(dmavRoundtrip);

        ComparisonProfile profile = dmavRoundtripProfile();
        List<LossEvent> losses = readLosses(reverseReportDir);
        ComparisonReport report = new SemanticTransferComparator()
                .compare(original, roundtripped, profile, losses);

        assertThat(report.equivalent())
                .as("Semantic differences: %s", report.errors())
                .isTrue();
        assertThat(report.observedLossReasonCodes()).contains("DMAV_ONLY");
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add("https://models.interlis.ch");
        DiagnosticCollector diagnostics = new JobRunner().run(mappingPath,
                new RunOptions(modelDirs, true, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-" + outputPath.getFileName() + ".yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"",
                        "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-lfp3.xtf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private Path materializeDmavToDm01(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-" + outputPath.getFileName() + ".yaml");
        String yaml = Files.readString(DMAV_TO_DM01_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"",
                        "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-lfp3.itf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private List<IomObject> semanticDm01Objects(Path path) throws Exception {
        return readObjects(path, dm01Td).stream()
                .filter(obj -> hasSuffix(obj, ".LFP3Nachfuehrung") || hasSuffix(obj, ".LFP3"))
                .toList();
    }

    private List<IomObject> semanticDmavObjects(Path path) throws Exception {
        return readObjects(path, dmavTd).stream()
                .filter(obj -> hasSuffix(obj, ".LFP3Nachfuehrung")
                        || (hasSuffix(obj, ".LFP3") && "LFP3".equals(obj.getattrvalue("LFPArt"))))
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
                .businessKey("LFP3Nachfuehrung", "NBIdent", "Identifikator")
                .businessKey("LFP3", "NBIdent", "Nummer")
                .numericTolerance(0.001)
                .ignore("Entstehung")
                .ignore("Protokoll")
                .build();
    }

    private ComparisonProfile dmavRoundtripProfile() {
        return ComparisonProfile.builder()
                .businessKey("LFP3Nachfuehrung", "NBIdent", "Identifikator")
                .businessKey("LFP3", "NBIdent", "Nummer")
                .numericTolerance(0.001)
                .ignore("Entstehung")
                .ignore("Schutzart")
                .ignore("Grenzpunktfunktion")
                .ignore("IstHoheitsgrenzsteinAlt")
                .ignore("AktiverUnterhalt")
                .ignore("SymbolOri")
                .expectedLossReasonCode("DMAV_ONLY")
                .build();
    }

    private List<LossEvent> readLosses(Path reportDir) throws Exception {
        Path path = reportDir.resolve("lossiness-report.json");
        if (!Files.exists(path)) {
            return List.of();
        }
        return JSON.readValue(path.toFile(), new TypeReference<>() {});
    }

    private long countBySuffix(List<IomObject> objects, String suffix) {
        return objects.stream().filter(obj -> hasSuffix(obj, suffix)).count();
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
