package guru.interlis.transformer.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExecutionMetricsTest {

    @Test
    void snapshotCapturesAllCounters() {
        ExecutionMetrics metrics = new ExecutionMetrics();
        metrics.recordReadStart();
        metrics.recordRuleMatch("r1");
        metrics.recordRuleMatch("r2");
        metrics.recordJoinLookup();
        metrics.recordBagLookup();
        metrics.recordTarget("MyClass");
        metrics.recordTarget("MyClass");
        metrics.recordTarget("OtherClass");
        metrics.recordFiltered();
        metrics.recordReadEnd(10);

        ExecutionMetricsSnapshot snapshot = metrics.snapshot();

        assertThat(snapshot.sourceRecordsRead()).isEqualTo(10);
        assertThat(snapshot.sourceRecordsFiltered()).isEqualTo(1);
        assertThat(snapshot.targetsCreated()).isEqualTo(3);
        assertThat(snapshot.joinLookups()).isEqualTo(1);
        assertThat(snapshot.bagLookups()).isEqualTo(1);
        assertThat(snapshot.ruleMatches()).isEqualTo(2);
        assertThat(snapshot.elapsedMillis()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.targetsByClass()).containsEntry("MyClass", 2L);
        assertThat(snapshot.targetsByClass()).containsEntry("OtherClass", 1L);
        assertThat(snapshot.summary()).contains("10 read");
    }

    @Test
    void snapshotWithoutReadReturnsZeroElapsed() {
        ExecutionMetrics metrics = new ExecutionMetrics();
        metrics.recordRuleMatch("r1");
        metrics.recordTarget("C");

        ExecutionMetricsSnapshot snapshot = metrics.snapshot();

        assertThat(snapshot.elapsedMillis()).isEqualTo(0);
        assertThat(snapshot.sourceRecordsRead()).isEqualTo(0);
        assertThat(snapshot.targetsCreated()).isEqualTo(1);
    }

    @Test
    void snapshotSummaryIncludesKeyMetrics() {
        ExecutionMetrics metrics = new ExecutionMetrics();
        metrics.recordReadStart();
        metrics.recordRuleMatch("r1");
        metrics.recordJoinLookup();
        metrics.recordJoinLookup();
        metrics.recordBagLookup();
        metrics.recordTarget("C");
        metrics.recordReadEnd(5);

        String summary = metrics.snapshot().summary();
        assertThat(summary).contains("5 read");
        assertThat(summary).contains("1 targets created");
        assertThat(summary).contains("2 joins");
        assertThat(summary).contains("1 bags");
        assertThat(summary).contains("1 rule matches");
    }
}
