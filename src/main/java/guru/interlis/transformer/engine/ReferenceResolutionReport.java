package guru.interlis.transformer.engine;

public record ReferenceResolutionReport(
        int resolved,
        int unresolvedOptional,
        int unresolvedMandatory,
        int ambiguous,
        int typeMismatch,
        int cardinalityViolations,
        int unrepresentable,
        int totalDeferred) {

    public boolean hasErrors() {
        return unresolvedMandatory > 0
                || ambiguous > 0
                || typeMismatch > 0
                || cardinalityViolations > 0
                || unrepresentable > 0;
    }
}
