package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;

public record DeferredReference(
        IomObject owner,
        String ownerOutputId,
        String ownerRuleId,
        String ownerBasketId,
        String targetRoleName,
        String associationName,
        java.util.List<SourceReferenceSelector> sourceSelectors,
        String targetRuleId,
        boolean singleton,
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
