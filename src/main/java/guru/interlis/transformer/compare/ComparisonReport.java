package guru.interlis.transformer.compare;

import java.util.List;
import java.util.Set;

public record ComparisonReport(
        int leftObjectCount,
        int rightObjectCount,
        int matchedObjectCount,
        List<ComparisonIssue> issues,
        Set<String> observedLossReasonCodes,
        Set<String> expectedLossReasonCodes) {
    public ComparisonReport {
        issues = issues != null ? List.copyOf(issues) : List.of();
        observedLossReasonCodes = observedLossReasonCodes != null ? Set.copyOf(observedLossReasonCodes) : Set.of();
        expectedLossReasonCodes = expectedLossReasonCodes != null ? Set.copyOf(expectedLossReasonCodes) : Set.of();
    }

    public boolean equivalent() {
        return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
    }

    public List<ComparisonIssue> errors() {
        return issues.stream()
                .filter(issue -> issue.severity() == Severity.ERROR)
                .toList();
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public record ComparisonIssue(
            Severity severity, String path, String message, String leftValue, String rightValue) {}
}
