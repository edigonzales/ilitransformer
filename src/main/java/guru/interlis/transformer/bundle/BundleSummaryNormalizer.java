package guru.interlis.transformer.bundle;

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

public final class BundleSummaryNormalizer {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper =
            new ObjectMapper(new YAMLFactory()).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public BundleSummary normalize(Path reportJsonPath, BundleManifest manifest) throws IOException {
        Map<String, Object> report = readReport(reportJsonPath);

        BundleSummary summary = new BundleSummary();
        summary.name = manifest.name;
        summary.direction = manifest.direction;
        summary.sourceSha256 = manifest.source.sha256;

        extractCounts(report, summary.counts);
        extractTargetsByClass(report, summary.targetsByClass);
        extractWarnings(report, summary.warningsByCode, summary.warningsByRule);

        return summary;
    }

    public Map<String, Object> readReport(Path reportJsonPath) throws IOException {
        return jsonMapper.readValue(reportJsonPath.toFile(), MAP_TYPE);
    }

    public void extractCounts(Map<String, Object> report, BundleSummary.Counts counts) {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw =
                report.get("counts") instanceof Map<?, ?> rawCounts ? (Map<String, Object>) rawCounts : Map.of();
        counts.sourceRecordsRead = intValue(raw.get("sourceRecordsRead"));
        counts.sourceRecordsFiltered = intValue(raw.get("sourceRecordsFiltered"));
        counts.targetsCreated = intValue(raw.get("targetsCreated"));
        counts.targetsWritten = intValue(raw.get("targetsWritten"));
        counts.errors = intValue(raw.get("errors"));
        counts.warnings = intValue(raw.get("warnings"));
    }

    public void extractTargetsByClass(Map<String, Object> report, Map<String, Long> targetsByClass) {
        @SuppressWarnings("unchecked")
        Map<String, Object> performance = report.get("performance") instanceof Map<?, ?> rawPerformance
                ? (Map<String, Object>) rawPerformance
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> rawTargets = performance.get("targetsByClass") instanceof Map<?, ?> targets
                ? (Map<String, Object>) targets
                : Map.of();
        rawTargets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> targetsByClass.put(entry.getKey(), longValue(entry.getValue())));
    }

    public void extractWarnings(
            Map<String, Object> report,
            Map<String, Integer> warningsByCode,
            Map<String, Map<String, Integer>> warningsByRule) {
        @SuppressWarnings("unchecked")
        List<Object> diagnostics =
                report.get("diagnostics") instanceof List<?> rawDiagnostics ? (List<Object>) rawDiagnostics : List.of();

        Map<String, Integer> byCode = new TreeMap<>();
        Map<String, Map<String, Integer>> byRule = new TreeMap<>();
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
            byCode.merge(code, 1, Integer::sum);
            byRule.computeIfAbsent(ruleId, ignored -> new TreeMap<>()).merge(code, 1, Integer::sum);
        }

        byCode.forEach(warningsByCode::put);
        byRule.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> warningsByRule.put(entry.getKey(), new LinkedHashMap<>(entry.getValue())));
    }

    public BundleSummary readSummary(Path path) throws IOException {
        return yamlMapper.readValue(path.toFile(), BundleSummary.class);
    }

    public void writeSummary(Path path, BundleSummary summary) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        yamlMapper.writeValue(path.toFile(), summary);
    }

    public boolean summariesMatch(BundleSummary expected, BundleSummary actual) {
        return yamlMapper.valueToTree(expected).equals(yamlMapper.valueToTree(actual));
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
