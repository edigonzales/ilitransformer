package guru.interlis.transformer.dmav;

import java.nio.file.Path;
import java.util.List;

public final class Dm01DmavPaths {

    public static final String LOCAL_MODEL_DIR = "src/test/data/av/models/";
    public static final String REMOTE_MODEL_DIR = "https://models.interlis.ch";
    public static final String LOCAL_AND_REMOTE_MODEL_DIRS = LOCAL_MODEL_DIR + ";" + REMOTE_MODEL_DIR;

    public static final Path CORRELATION_XLSX =
            Path.of("docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx");
    public static final Path GENERATED_HINTS =
            Path.of("build/generated/dm01-dmav/correlation-hints.json");
    public static final Path GENERATED_CANDIDATES =
            Path.of("build/generated/dm01-dmav/mapping-candidates.json");
    public static final Path GENERATED_DM01_TO_DMAV_YAML =
            Path.of("build/generated/dm01-dmav/dm01-to-dmav.generated.yaml");
    public static final Path GENERATED_DMAV_TO_DM01_YAML =
            Path.of("build/generated/dm01-dmav/dmav-to-dm01.generated.yaml");
    public static final Path CANDIDATE_REPORT =
            Path.of("build/reports/dm01-dmav/candidate-report.md");
    public static final Path CORRELATION_IMPORT_REPORT =
            Path.of("build/reports/dm01-dmav/correlation-import-report.md");
    public static final Path TOPIC_GAP_REPORT =
            Path.of("build/reports/dm01-dmav/topic-gap-report.md");

    private Dm01DmavPaths() {}

    public static List<String> defaultModelDirs() {
        return List.of(LOCAL_MODEL_DIR, REMOTE_MODEL_DIR);
    }
}
