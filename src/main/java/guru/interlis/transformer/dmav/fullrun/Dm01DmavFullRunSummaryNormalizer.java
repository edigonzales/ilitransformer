package guru.interlis.transformer.dmav.fullrun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class Dm01DmavFullRunSummaryNormalizer {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper =
            new ObjectMapper(new YAMLFactory()).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Dm01DmavFullRunSummary normalize(Path reportJsonPath, Dm01DmavFullRunManifest manifest) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> report = jsonMapper.readValue(reportJsonPath.toFile(), MAP_TYPE);

        Dm01DmavFullRunSummary summary = new Dm01DmavFullRunSummary();
        summary.datasetSlug = manifest.datasetSlug;
        summary.direction = manifest.direction;
        summary.sourceSha256 = manifest.source.sha256;

        @SuppressWarnings("unchecked")
        Map<String, Object> counts =
                report.get("counts") instanceof Map<?, ?> rawCounts ? (Map<String, Object>) rawCounts : Map.of();
        summary.counts.sourceRecordsRead = intValue(counts.get("sourceRecordsRead"));
        summary.counts.sourceRecordsFiltered = intValue(counts.get("sourceRecordsFiltered"));
        summary.counts.targetsCreated = intValue(counts.get("targetsCreated"));
        summary.counts.targetsWritten = intValue(counts.get("targetsWritten"));
        summary.counts.errors = intValue(counts.get("errors"));
        summary.counts.warnings = intValue(counts.get("warnings"));

        for (Dm01DmavFullRunManifest.TopicMappingSpec topic : manifest.topics.include) {
            summary.includedTopics.add(topic.id);
        }
        if (manifest.topics.exclude != null) {
            for (Dm01DmavFullRunManifest.ExcludedTopicSpec excluded : manifest.topics.exclude) {
                Dm01DmavFullRunSummary.ExcludedTopicSummary item = new Dm01DmavFullRunSummary.ExcludedTopicSummary();
                item.id = excluded.id;
                item.reason = excluded.reason;
                summary.excludedTopics.add(item);
            }
        }
        summary.sourceTopicsWithoutProfile.addAll(manifest.sourceTopicsWithoutProfile);
        summary.sourceTopicsPresentInDataset.addAll(manifest.sourceTopicsPresentInDataset);

        @SuppressWarnings("unchecked")
        Map<String, Object> performance = report.get("performance") instanceof Map<?, ?> rawPerformance
                ? (Map<String, Object>) rawPerformance
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> targetsByClass = performance.get("targetsByClass") instanceof Map<?, ?> rawTargets
                ? (Map<String, Object>) rawTargets
                : Map.of();
        targetsByClass.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> summary.targetsByClass.put(entry.getKey(), longValue(entry.getValue())));

        @SuppressWarnings("unchecked")
        List<Object> diagnostics =
                report.get("diagnostics") instanceof List<?> rawDiagnostics ? (List<Object>) rawDiagnostics : List.of();

        Map<String, Integer> warningsByCode = new TreeMap<>();
        Map<String, Map<String, Integer>> warningsByRule = new TreeMap<>();
        for (Object rawDiagnostic : diagnostics) {
            if (!(rawDiagnostic instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> diagnostic = (Map<String, Object>) rawMap;
            if (!"WARNING".equals(String.valueOf(diagnostic.get("severity")))) {
                continue;
            }
            String code = String.valueOf(diagnostic.get("code"));
            String ruleId = diagnostic.get("ruleId") == null ? "-" : String.valueOf(diagnostic.get("ruleId"));
            warningsByCode.merge(code, 1, Integer::sum);
            warningsByRule.computeIfAbsent(ruleId, ignored -> new TreeMap<>()).merge(code, 1, Integer::sum);
        }

        warningsByCode.forEach(summary.warningsByCode::put);
        warningsByRule.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> summary.warningsByRule.put(entry.getKey(), new LinkedHashMap<>(entry.getValue())));

        return summary;
    }

    public Dm01DmavFullRunSummary readSummary(Path path) throws IOException {
        return yamlMapper.readValue(path.toFile(), Dm01DmavFullRunSummary.class);
    }

    public void writeSummary(Path path, Dm01DmavFullRunSummary summary) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        yamlMapper.writeValue(path.toFile(), summary);
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
