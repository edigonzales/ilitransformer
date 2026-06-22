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
        metrics.recordWritten(3);
        metrics.recordReadEnd(10);

        ExecutionMetricsSnapshot snapshot = metrics.snapshot();

        assertThat(snapshot.sourceRecordsRead()).isEqualTo(10);
        assertThat(snapshot.sourceRecordsFiltered()).isEqualTo(1);
        assertThat(snapshot.targetsCreated()).isEqualTo(3);
        assertThat(snapshot.targetsWritten()).isEqualTo(3);
        assertThat(snapshot.joinLookups()).isEqualTo(1);
        assertThat(snapshot.bagLookups()).isEqualTo(1);
        assertThat(snapshot.ruleMatches()).isEqualTo(2);
        assertThat(snapshot.elapsedMillis()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.sourceIndexMs()).isZero();
        assertThat(snapshot.ruleExecutionMs()).isZero();
        assertThat(snapshot.referenceResolutionMs()).isZero();
        assertThat(snapshot.outputWriteMs()).isZero();
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

    @Test
    void snapshotCapturesPhaseAndRuleMetrics() {
        ExecutionMetrics metrics = new ExecutionMetrics();
        metrics.recordReadEnd(3);
        metrics.recordSourceIndexDuration(2_000_000);
        metrics.recordRuleSourceRecordVisited("copy-rule");
        metrics.recordRuleMatch("copy-rule");
        metrics.recordTarget("Target.A");
        metrics.recordRuleExecution("copy-rule", 1_000_000, 1, 0, 1);

        metrics.recordRuleSourceRecordVisited("join-rule");
        metrics.recordRuleSourceRecordVisited("join-rule");
        metrics.recordJoinLookup();
        metrics.recordFiltered();
        metrics.recordRuleExecution("join-rule", 3_000_000, 0, 1, 0);
        metrics.recordRuleExecutionDuration(4_000_000);
        metrics.recordReferenceResolutionDuration(1_000_000);
        metrics.recordOutputWriteDuration(5_000_000);

        ExecutionMetricsSnapshot snapshot = metrics.snapshot();

        assertThat(snapshot.elapsedMillis()).isEqualTo(12);
        assertThat(snapshot.sourceIndexMs()).isEqualTo(2);
        assertThat(snapshot.ruleExecutionMs()).isEqualTo(4);
        assertThat(snapshot.referenceResolutionMs()).isEqualTo(1);
        assertThat(snapshot.outputWriteMs()).isEqualTo(5);
        assertThat(snapshot.rules()).extracting(RuleMetricsSnapshot::ruleId).containsExactly("copy-rule", "join-rule");
        assertThat(snapshot.rules().get(0).sourceRecordsVisited()).isEqualTo(1);
        assertThat(snapshot.rules().get(0).matches()).isEqualTo(1);
        assertThat(snapshot.rules().get(0).targetsCreated()).isEqualTo(1);
        assertThat(snapshot.rules().get(1).sourceRecordsVisited()).isEqualTo(2);
        assertThat(snapshot.rules().get(1).filtered()).isEqualTo(1);
        assertThat(snapshot.rules().get(1).elapsedMillis()).isGreaterThanOrEqualTo(0);
    }
}
