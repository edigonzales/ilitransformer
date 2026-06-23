package guru.interlis.transformer.dmav.fullrun;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.dmav.Dm01DmavPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dm01DmavFullRunSummaryNormalizerTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath().normalize();

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final Dm01DmavFullRunManifestLoader manifestLoader = new Dm01DmavFullRunManifestLoader();
    private final Dm01DmavFullRunSummaryNormalizer normalizer = new Dm01DmavFullRunSummaryNormalizer();

    @TempDir
    Path tempDir;

    @Test
    void normalizesReportToCheckedInExpectedSummary() throws Exception {
        Path manifestPath = Dm01DmavPaths.fullRunBundleDir("so-2549").resolve("manifest.yaml");
        Dm01DmavFullRunManifest manifest = manifestLoader.load(manifestPath, REPOSITORY_ROOT);
        Path expectedSummaryPath =
                manifestLoader.resolveManifestPath(manifestPath, REPOSITORY_ROOT, manifest.report.expectedSummary);
        Dm01DmavFullRunSummary expected = normalizer.readSummary(expectedSummaryPath);

        Path reportPath = tempDir.resolve("transformation-report.json");
        JSON_MAPPER.writeValue(reportPath.toFile(), syntheticReport(expected));

        Dm01DmavFullRunSummary actual = normalizer.normalize(reportPath, manifest);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);

        Path normalizedSummaryPath = tempDir.resolve("normalized-summary.yaml");
        normalizer.writeSummary(normalizedSummaryPath, actual);
        Dm01DmavFullRunSummary reloaded = normalizer.readSummary(normalizedSummaryPath);
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void normalizesDmavToDm01ReportToCheckedInExpectedSummary() throws Exception {
        Path manifestPath = Dm01DmavPaths.fullRunBundleDir("dmav-tym-alles-v1-1").resolve("manifest.yaml");
        Dm01DmavFullRunManifest manifest = manifestLoader.load(manifestPath, REPOSITORY_ROOT);
        Path expectedSummaryPath =
                manifestLoader.resolveManifestPath(manifestPath, REPOSITORY_ROOT, manifest.report.expectedSummary);
        Dm01DmavFullRunSummary expected = normalizer.readSummary(expectedSummaryPath);

        Path reportPath = tempDir.resolve("dmav-to-dm01-transformation-report.json");
        JSON_MAPPER.writeValue(reportPath.toFile(), syntheticReport(expected));

        Dm01DmavFullRunSummary actual = normalizer.normalize(reportPath, manifest);

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    private static Map<String, Object> syntheticReport(Dm01DmavFullRunSummary expected) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(
                "counts",
                Map.of(
                        "sourceRecordsRead", expected.counts.sourceRecordsRead,
                        "sourceRecordsFiltered", expected.counts.sourceRecordsFiltered,
                        "targetsCreated", expected.counts.targetsCreated,
                        "targetsWritten", expected.counts.targetsWritten,
                        "errors", expected.counts.errors,
                        "warnings", expected.counts.warnings));
        report.put("performance", Map.of("targetsByClass", expected.targetsByClass, "elapsedMs", 1234));

        List<Map<String, Object>> diagnostics = new ArrayList<>();
        diagnostics.add(Map.of("severity", "INFO", "code", "IGNORED", "message", "ignored"));
        expected.warningsByRule.forEach((ruleId, warningsByCode) -> warningsByCode.forEach((code, count) -> {
            for (int index = 0; index < count; index++) {
                diagnostics.add(Map.of(
                        "severity",
                        "WARNING",
                        "code",
                        code,
                        "ruleId",
                        ruleId,
                        "message",
                        "synthetic warning " + index));
            }
        }));
        report.put("diagnostics", diagnostics);
        return report;
    }
}
