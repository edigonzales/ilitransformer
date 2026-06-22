package guru.interlis.transformer.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExecutionMetricsSnapshot(
        long sourceRecordsRead,
        long sourceRecordsFiltered,
        long targetsCreated,
        long targetsWritten,
        long joinLookups,
        long bagLookups,
        long ruleMatches,
        Map<String, Long> targetsByClass,
        long sourceIndexMs,
        long ruleExecutionMs,
        long referenceResolutionMs,
        long outputWriteMs,
        List<RuleMetricsSnapshot> rules,
        long elapsedMillis) {
    public ExecutionMetricsSnapshot {
        targetsByClass = Collections.unmodifiableMap(new LinkedHashMap<>(targetsByClass));
        rules = List.copyOf(rules);
    }

    public String summary() {
        return String.format(
                "Metrics: %d read, %d filtered, %d targets created, %d written, %d joins, %d bags, %d rule matches in %d ms",
                sourceRecordsRead,
                sourceRecordsFiltered,
                targetsCreated,
                targetsWritten,
                joinLookups,
                bagLookups,
                ruleMatches,
                elapsedMillis);
    }
}
