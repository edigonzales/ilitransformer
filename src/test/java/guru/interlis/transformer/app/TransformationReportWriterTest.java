package guru.interlis.transformer.app;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.ExecutionMetricsSnapshot;
import guru.interlis.transformer.engine.RuleMetricsSnapshot;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.mapping.plan.BasketPlan;
import guru.interlis.transformer.mapping.plan.CompileMode;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.OidPlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.state.BasketStrategy;
import guru.interlis.transformer.state.OidStrategy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransformationReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void jsonReportIncludesRunProfileAndPhaseMetrics() throws Exception {
        Path report = tempDir.resolve("transformation-report.json");
        TransformPlan plan = plan();
        TransformResult result = new TransformResult(10, 2, 8, 8, 0, 1, "DETERMINISTIC_UUID", "BY_TOPIC");
        ExecutionMetricsSnapshot metrics = new ExecutionMetricsSnapshot(
                10,
                2,
                8,
                0,
                3,
                1,
                8,
                Map.of("Model.Topic.Target", 8L),
                11,
                22,
                3,
                4,
                List.of(new RuleMetricsSnapshot("join-rule", 5, 4, 1, 4, 7)),
                40);
        RunProfileSnapshot profile = new RunProfileSnapshot(12, 34, 5, 60);

        new TransformationReportWriter()
                .writeJson(
                        report,
                        plan,
                        result,
                        new DiagnosticCollector(),
                        List.of(),
                        Duration.ofMillis(60),
                        Map.of(),
                        metrics,
                        profile);

        Map<String, Object> root = new ObjectMapper().readValue(report.toFile(), new TypeReference<>() {});
        assertThat(map(root.get("counts"))).containsEntry("sourceRecordsRead", 10);
        assertThat(map(root.get("runProfile")))
                .containsEntry("compilePrepareMs", 12)
                .containsEntry("validationMs", 34)
                .containsEntry("reportWriteMs", 5)
                .containsEntry("totalRunMs", 60);
        Map<String, Object> performance = map(root.get("performance"));
        assertThat(performance)
                .containsEntry("sourceIndexMs", 11)
                .containsEntry("ruleExecutionMs", 22)
                .containsEntry("referenceResolutionMs", 3)
                .containsEntry("outputWriteMs", 4);
        assertThat((List<?>) performance.get("rules")).singleElement().satisfies(rule -> assertThat(map(rule))
                .containsEntry("ruleId", "join-rule")
                .containsEntry("sourceRecordsVisited", 5));
    }

    @Test
    void markdownReportIncludesProfileSections() throws Exception {
        Path report = tempDir.resolve("transformation-report.md");

        new TransformationReportWriter()
                .writeMarkdown(
                        report,
                        plan(),
                        new TransformResult(1, 0, 1, 1, 0, 0, "INTEGER", "PRESERVE"),
                        new DiagnosticCollector(),
                        List.of(),
                        Duration.ofMillis(1),
                        Map.of(),
                        new ExecutionMetricsSnapshot(
                                1,
                                0,
                                1,
                                0,
                                0,
                                0,
                                1,
                                Map.of(),
                                1,
                                1,
                                1,
                                1,
                                List.of(new RuleMetricsSnapshot("copy-rule", 1, 1, 0, 1, 1)),
                                4),
                        new RunProfileSnapshot(1, 2, 3, 6));

        String markdown = java.nio.file.Files.readString(report);
        assertThat(markdown).contains("## Run Profile");
        assertThat(markdown).contains("| Source index (ms) | 1 |");
        assertThat(markdown).contains("### Rules");
        assertThat(markdown).contains("| copy-rule | 1 | 1 | 0 | 1 | 1 |");
    }

    private static TransformPlan plan() {
        return new TransformPlan(
                "test-job",
                "test-direction",
                FailPolicy.STRICT,
                CompileMode.STRICT,
                List.of(),
                Map.of(),
                Map.of(),
                new DiagnosticCollector(),
                new OidPlan(OidStrategy.INTEGER, "test"),
                new BasketPlan(BasketStrategy.PRESERVE),
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
