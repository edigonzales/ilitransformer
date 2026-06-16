package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("real-data")
class ExtractedBbDmavFixtureValidationTest {

    private static final Path DATA_DIR = Dm01DmavPaths.FULL_DATASET_DIR;
    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String UMBRELLA_MODEL = Dm01DmavPaths.DMAV_UMBRELLA_MODEL;

    private static IliModelService modelService;
    private static ConnectedSubgraphExtractor extractor;
    private static Path dmavFile;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        modelService = new IliModelService();
        extractor = new ConnectedSubgraphExtractor(modelService);

        try (var files = Files.walk(DATA_DIR)) {
            dmavFile = files.filter(
                            f -> f.getFileName().toString().toLowerCase().endsWith(".xtf"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No XTF file found under " + DATA_DIR));
        }
    }

    @Test
    void extractAndValidateDmavBbFixture() throws Exception {
        IliModelCompileResult compileResult =
                modelService.compileModel(UMBRELLA_MODEL, Dm01DmavPaths.LOCAL_AND_REMOTE_MODEL_DIRS);
        assertThat(compileResult.hasErrors())
                .as("DMAV model compilation: " + compileResult.diagnostics())
                .isFalse();

        TransferDatasetDescriptor source = new TransferDatasetDescriptor(
                dmavFile.getFileName().toString(),
                dmavFile.toAbsolutePath(),
                TransferFormat.XTF,
                List.of(UMBRELLA_MODEL),
                List.of(Dm01DmavPaths.LOCAL_MODEL_DIR, Dm01DmavPaths.REMOTE_MODEL_DIR),
                dmavFile.toFile().length());

        Path fixtureDir = tempDir.resolve("fixtures");
        Files.createDirectories(fixtureDir);

        ExtractionRequest request = Dm01DmavFixtures.bbDmavExtractionRequest(fixtureDir);
        ExtractedTransfer result = extractor.extract(source, request);

        assertThat(result.totalObjects()).isGreaterThanOrEqualTo(3);
        assertThat(result.includedClasses().stream().anyMatch(c -> c.contains("BBNachfuehrung")))
                .isTrue();
        assertThat(result.includedClasses().stream().anyMatch(c -> c.contains("Messpunkt")))
                .isTrue();

        TransferValidationService validator = new InProcessIlivalidatorService();
        Path logFile = tempDir.resolve("bb-dmav-validation.log");
        ValidationResult validation = validator.validate(
                result.transferFile(),
                List.of(Dm01DmavPaths.LOCAL_MODEL_DIR, Dm01DmavPaths.REMOTE_MODEL_DIR),
                List.of(UMBRELLA_MODEL),
                logFile);

        assertThat(validation.valid())
                .as("DMAV BB fixture must be valid. Log: " + validation.logText())
                .isTrue();

        String content = Files.readString(result.transferFile(), StandardCharsets.UTF_8);
        assertThat(content).contains("BBNachfuehrung");
        assertThat(content).contains("Bodenbedeckung.Bodenbedeckung");
        assertThat(content).contains("Messpunkt");
        assertThat(content).doesNotContain("DMAV_Einzelobjekte_V1_1:Messpunkt");
        assertThat(content).doesNotContain("DMAV_Rohrleitungen_V1_1:Messpunkt");

        Path targetPath = Dm01DmavFixtures.BB.dmavRealExtractFixture();
        boolean updated = FixtureUpdateSupport.syncCheckedInFixture(result.transferFile(), targetPath);

        assertThat(targetPath).exists();
        assertThat(Files.size(targetPath)).isGreaterThan(0);
        if (updated) {
            System.out.println("Fixture updated: " + targetPath.toAbsolutePath());
        } else {
            System.out.println("Fixture validated without overwriting checked-in file. "
                    + "Use -PupdateFixtures=true to refresh " + targetPath.toAbsolutePath());
        }
    }
}
