package guru.interlis.transformer.engine;

public record RuleMetricsSnapshot(
        String ruleId,
        long sourceRecordsVisited,
        long matches,
        long filtered,
        long targetsCreated,
        long elapsedMillis) {}
