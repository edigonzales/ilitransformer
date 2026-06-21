package guru.interlis.transformer.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleSummaryNormalizerTest {

    private final BundleSummaryNormalizer normalizer = new BundleSummaryNormalizer();

    @TempDir
    Path tempDir;

    @Test
    void normalizesReportAndRoundtrips() throws Exception {
        Path reportJson = tempDir.resolve("transformation-report.json");
        Files.writeString(reportJson, """
                {
                  "counts": {
                    "sourceRecordsRead": 10,
                    "sourceRecordsFiltered": 2,
                    "targetsCreated": 8,
                    "targetsWritten": 8,
                    "errors": 0,
                    "warnings": 3
                  },
                  "performance": {
                    "targetsByClass": {
                      "Model.Topic.B": 5,
                      "Model.Topic.A": 3
                    }
                  },
                  "diagnostics": [
                    { "severity": "WARNING", "code": "W1", "ruleId": "r1" },
                    { "severity": "WARNING", "code": "W1", "ruleId": "r1" },
                    { "severity": "ERROR", "code": "E1", "ruleId": "r2" }
                  ]
                }
                """, StandardCharsets.UTF_8);

        BundleManifest manifest = new BundleManifest();
        manifest.name = "demo-bundle";
        manifest.direction = "dm01-to-dmav";
        manifest.source.sha256 = "deadbeef";

        BundleSummary summary = normalizer.normalize(reportJson, manifest);

        assertThat(summary.name).isEqualTo("demo-bundle");
        assertThat(summary.direction).isEqualTo("dm01-to-dmav");
        assertThat(summary.sourceSha256).isEqualTo("deadbeef");
        assertThat(summary.counts.sourceRecordsRead).isEqualTo(10);
        assertThat(summary.counts.targetsCreated).isEqualTo(8);
        assertThat(summary.counts.warnings).isEqualTo(3);
        assertThat(summary.targetsByClass).containsEntry("Model.Topic.A", 3L).containsEntry("Model.Topic.B", 5L);
        assertThat(summary.warningsByCode).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("W1", 2));
        assertThat(summary.warningsByRule).containsKey("r1");

        Path summaryYaml = tempDir.resolve("normalized-summary.yaml");
        normalizer.writeSummary(summaryYaml, summary);
        BundleSummary reloaded = normalizer.readSummary(summaryYaml);

        assertThat(normalizer.summariesMatch(summary, reloaded)).isTrue();
    }
}
