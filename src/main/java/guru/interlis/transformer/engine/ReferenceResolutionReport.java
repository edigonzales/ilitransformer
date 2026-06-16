package guru.interlis.transformer.engine;

public record ReferenceResolutionReport(
        int resolved,
        int unresolvedOptional,
        int unresolvedMandatory,
        int ambiguous,
        int typeMismatch,
        int cardinalityViolations,
        int totalDeferred) {

    public boolean hasErrors() {
        return unresolvedMandatory > 0 || ambiguous > 0 || typeMismatch > 0 || cardinalityViolations > 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int resolved;
        private int unresolvedOptional;
        private int unresolvedMandatory;
        private int ambiguous;
        private int typeMismatch;
        private int cardinalityViolations;
        private int totalDeferred;

        public Builder resolved(int v) {
            this.resolved = v;
            return this;
        }

        public Builder unresolvedOptional(int v) {
            this.unresolvedOptional = v;
            return this;
        }

        public Builder unresolvedMandatory(int v) {
            this.unresolvedMandatory = v;
            return this;
        }

        public Builder ambiguous(int v) {
            this.ambiguous = v;
            return this;
        }

        public Builder typeMismatch(int v) {
            this.typeMismatch = v;
            return this;
        }

        public Builder cardinalityViolations(int v) {
            this.cardinalityViolations = v;
            return this;
        }

        public Builder totalDeferred(int v) {
            this.totalDeferred = v;
            return this;
        }

        public ReferenceResolutionReport build() {
            return new ReferenceResolutionReport(
                    resolved,
                    unresolvedOptional,
                    unresolvedMandatory,
                    ambiguous,
                    typeMismatch,
                    cardinalityViolations,
                    totalDeferred);
        }
    }
}
