package guru.interlis.transformer.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExecutionMetrics {
    private long readStartMs;
    private long readEndMs;
    private long sourceRecordsRead;
    private long sourceRecordsFiltered;
    private long targetsCreated;
    private long targetsWritten;
    private long joinLookups;
    private long bagLookups;
    private long ruleMatches;
    private final Map<String, Long> targetsByClass = new LinkedHashMap<>();
    private long sourceIndexNanos;
    private long ruleExecutionNanos;
    private long referenceResolutionNanos;
    private long outputWriteNanos;
    private final Map<String, MutableRuleMetrics> ruleMetrics = new LinkedHashMap<>();

    public void recordReadStart() {
        readStartMs = System.currentTimeMillis();
    }

    public void recordReadEnd(int recordCount) {
        readEndMs = System.currentTimeMillis();
        sourceRecordsRead = recordCount;
    }

    public void recordSourceIndexDuration(long nanos) {
        sourceIndexNanos += Math.max(0, nanos);
    }

    public void recordRuleExecutionDuration(long nanos) {
        ruleExecutionNanos += Math.max(0, nanos);
    }

    public void recordReferenceResolutionDuration(long nanos) {
        referenceResolutionNanos += Math.max(0, nanos);
    }

    public void recordOutputWriteDuration(long nanos) {
        outputWriteNanos += Math.max(0, nanos);
    }

    public void recordRuleMatch(String ruleId) {
        ruleMatches++;
    }

    public void recordRuleSourceRecordVisited(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) return;
        ruleMetrics(ruleId).sourceRecordsVisited++;
    }

    public void recordRuleExecution(
            String ruleId, long elapsedNanos, long matches, long filtered, long targetsCreated) {
        if (ruleId == null || ruleId.isBlank()) return;
        MutableRuleMetrics metrics = ruleMetrics(ruleId);
        metrics.elapsedNanos += Math.max(0, elapsedNanos);
        metrics.matches += Math.max(0, matches);
        metrics.filtered += Math.max(0, filtered);
        metrics.targetsCreated += Math.max(0, targetsCreated);
    }

    public void recordJoinLookup() {
        joinLookups++;
    }

    public void recordBagLookup() {
        bagLookups++;
    }

    public void recordTarget(String targetClass) {
        targetsCreated++;
        targetsByClass.merge(targetClass, 1L, Long::sum);
    }

    public void recordFiltered() {
        sourceRecordsFiltered++;
    }

    public void recordWritten() {
        targetsWritten++;
    }

    public void recordWritten(long count) {
        targetsWritten += Math.max(0, count);
    }

    public ExecutionMetricsSnapshot snapshot() {
        long phaseElapsed = sourceIndexNanos + ruleExecutionNanos + referenceResolutionNanos + outputWriteNanos;
        long elapsed = phaseElapsed > 0 ? toMillis(phaseElapsed) : readEndMs > 0 ? readEndMs - readStartMs : 0;
        return new ExecutionMetricsSnapshot(
                sourceRecordsRead,
                sourceRecordsFiltered,
                targetsCreated,
                targetsWritten,
                joinLookups,
                bagLookups,
                ruleMatches,
                new LinkedHashMap<>(targetsByClass),
                toMillis(sourceIndexNanos),
                toMillis(ruleExecutionNanos),
                toMillis(referenceResolutionNanos),
                toMillis(outputWriteNanos),
                ruleMetrics.values().stream().map(MutableRuleMetrics::snapshot).toList(),
                elapsed);
    }

    public long getSourceRecordsRead() {
        return sourceRecordsRead;
    }

    public long getSourceRecordsFiltered() {
        return sourceRecordsFiltered;
    }

    public long getTargetsCreated() {
        return targetsCreated;
    }

    public long getTargetsWritten() {
        return targetsWritten;
    }

    public long getJoinLookups() {
        return joinLookups;
    }

    public long getBagLookups() {
        return bagLookups;
    }

    public long getRuleMatches() {
        return ruleMatches;
    }

    public Map<String, Long> getTargetsByClass() {
        return new LinkedHashMap<>(targetsByClass);
    }

    private MutableRuleMetrics ruleMetrics(String ruleId) {
        return ruleMetrics.computeIfAbsent(ruleId, MutableRuleMetrics::new);
    }

    private static long toMillis(long nanos) {
        return nanos <= 0 ? 0 : Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanos));
    }

    private static final class MutableRuleMetrics {
        private final String ruleId;
        private long sourceRecordsVisited;
        private long matches;
        private long filtered;
        private long targetsCreated;
        private long elapsedNanos;

        private MutableRuleMetrics(String ruleId) {
            this.ruleId = ruleId;
        }

        private RuleMetricsSnapshot snapshot() {
            return new RuleMetricsSnapshot(
                    ruleId, sourceRecordsVisited, matches, filtered, targetsCreated, toMillis(elapsedNanos));
        }
    }
}
