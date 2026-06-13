package guru.interlis.transformer.dmav;

import java.nio.file.Path;
import java.util.List;

public final class Dm01DmavPaths {

    public static final String TOPIC_LFP3 = "lfp3";
    public static final String TOPIC_HFP3 = "hfp3";
    public static final String TOPIC_BB = "bb";

    public static final String LOCAL_MODEL_DIR = "src/test/data/av/models/";
    public static final String REMOTE_MODEL_DIR = "https://models.interlis.ch";
    public static final String LOCAL_AND_REMOTE_MODEL_DIRS = LOCAL_MODEL_DIR + ";" + REMOTE_MODEL_DIR;
    public static final String DM01_MODEL = "DM01AVCH24LV95D";
    public static final String DMAV_UMBRELLA_MODEL = "DMAVTYM_Alles_V1_1";
    public static final String DMAV_LFP3_MODEL = "DMAV_FixpunkteAVKategorie3_V1_1";
    public static final String DMAV_BB_MODEL = "DMAV_Bodenbedeckung_V1_1";

    public static final Path PROFILE_ROOT = Path.of("profiles");
    public static final Path FIXTURE_ROOT = Path.of("src/test/resources/fixtures/dm01-dmav");
    public static final Path FULL_DATASET_DIR = Path.of("src/test/data/DMAV_Version_1_1");
    public static final Path FULL_DM01_DATASET = FULL_DATASET_DIR.resolve("DM01-AV-CH.itf");
    public static final Path FULL_DMAV_DATASET = FULL_DATASET_DIR.resolve("DMAVTYM_Alles_V1_1.xtf");

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

    public static List<String> localModelDirs() {
        return List.of(LOCAL_MODEL_DIR);
    }

    public static List<String> defaultModelDirs() {
        return List.of(LOCAL_MODEL_DIR, REMOTE_MODEL_DIR);
    }

    public static Path fixtureDir(String topic) {
        return FIXTURE_ROOT.resolve(topic);
    }

    public static Path dm01MinimalFixture(String topic) {
        return fixtureDir(topic).resolve("dm01-minimal.itf");
    }

    public static Path dmavMinimalFixture(String topic) {
        return fixtureDir(topic).resolve("dmav-minimal.xtf");
    }

    public static Path dm01RealExtractFixture(String topic) {
        return fixtureDir(topic).resolve("dm01-real-extract.itf");
    }

    public static Path dmavRealExtractFixture(String topic) {
        return fixtureDir(topic).resolve("dmav-real-extract.xtf");
    }

    public static Path dm01ToDmavProfile(String topic) {
        return PROFILE_ROOT.resolve("dm01-to-dmav/1.1").resolve(topic + ".yaml");
    }

    public static Path dmavToDm01Profile(String topic) {
        return PROFILE_ROOT.resolve("dmav-to-dm01/1.1").resolve(topic + ".yaml");
    }
}
