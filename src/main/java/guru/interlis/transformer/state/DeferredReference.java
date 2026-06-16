package guru.interlis.transformer.state;

public record DeferredReference(
        TargetObjectKey owner,
        String targetRoleName,
        String associationName,
        SourceReferenceSelector sourceSelector,
        String targetRuleId,
        String expectedTargetClass,
        Cardinality expectedCardinality,
        boolean required) {

    public record Cardinality(long min, long max) {
        public static final long UNBOUND = Long.MAX_VALUE;

        public boolean isRequired() {
            return min > 0;
        }

        public boolean isUnbounded() {
            return max >= UNBOUND;
        }
    }
}
