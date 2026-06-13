package guru.interlis.transformer;

import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.model.ConnectedSubgraphExtractor;
import guru.interlis.transformer.model.ExtractedTransfer;
import guru.interlis.transformer.model.ExtractionRequest;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.testutil.TransferDatasetDescriptor;
import guru.interlis.transformer.testutil.TransferFormat;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;
import guru.interlis.transformer.validation.TransferValidationService;
import guru.interlis.transformer.validation.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("real-data")
class ExtractedEoDm01FixtureValidationTest {

    private static final Path DATA_DIR = Dm01DmavPaths.FULL_DATASET_DIR;
    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String DM01_MODEL = Dm01DmavPaths.DM01_MODEL;

    private static IliModelService modelService;
    private static ConnectedSubgraphExtractor extractor;
    private static Path dm01File;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        modelService = new IliModelService();
        extractor = new ConnectedSubgraphExtractor(modelService);

        try (var files = Files.walk(DATA_DIR)) {
            dm01File = files
                    .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".itf"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No ITF file found under " + DATA_DIR));
        }
    }

    @Test
    void extractAndValidateDm01EoFixture() throws Exception {
        TransferDatasetDescriptor source = new TransferDatasetDescriptor(
                dm01File.getFileName().toString(),
                dm01File.toAbsolutePath(),
                TransferFormat.ITF,
                List.of(DM01_MODEL),
                List.of(MODEL_DIR),
                dm01File.toFile().length());

        IliModelCompileResult compileResult = modelService.compileModel(DM01_MODEL, MODEL_DIR);
        assertThat(compileResult.hasErrors())
                .as("DM01 model compilation: " + compileResult.diagnostics())
                .isFalse();

        Path fixtureDir = tempDir.resolve("fixtures");
        Files.createDirectories(fixtureDir);

        ExtractionRequest request = Dm01DmavFixtures.eoDm01ExtractionRequest(fixtureDir);
        ExtractedTransfer result = extractor.extract(source, request);

        assertThat(result.totalObjects()).isGreaterThanOrEqualTo(4);
        assertThat(result.includedClasses().stream().anyMatch(c -> c.contains("EONachfuehrung"))).isTrue();
        assertThat(result.includedClasses().stream().anyMatch(c -> c.contains("Einzelobjekt"))).isTrue();

        TransferValidationService validator = new InProcessIlivalidatorService();
        Path logFile = tempDir.resolve("eo-dm01-validation.log");
        ValidationResult validation = validator.validate(
                result.transferFile(),
                List.of(MODEL_DIR),
                List.of(DM01_MODEL),
                logFile);

        assertThat(validation.valid())
                .as("DM01 EO fixture must be valid. Log: " + validation.logText())
                .isTrue();

        String content = Files.readString(result.transferFile(), StandardCharsets.ISO_8859_1);
        assertThat(content).contains("TOPI Einzelobjekte");
        assertThat(content).contains("TABL EONachfuehrung");
        assertThat(content).contains("TABL Einzelobjekt");

        Path targetPath = Dm01DmavFixtures.EO.dm01RealExtractFixture();
        boolean updated = FixtureUpdateSupport.syncCheckedInFixture(result.transferFile(), targetPath);

        if (updated) {
            assertThat(targetPath).exists();
            assertThat(Files.size(targetPath)).isGreaterThan(0);
            System.out.println("Fixture updated: " + targetPath.toAbsolutePath());
        } else {
            System.out.println("Fixture validated without overwriting checked-in file. "
                    + "Use -PupdateFixtures=true to refresh " + targetPath.toAbsolutePath());
        }
    }
}
