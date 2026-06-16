package guru.interlis.transformer.compare;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ComparisonProfile(
        double numericTolerance,
        Map<String, List<String>> businessKeys,
        Set<String> ignoredAttributes,
        Set<String> expectedLossReasonCodes) {
    public static final double DEFAULT_NUMERIC_TOLERANCE = 0.001;

    public ComparisonProfile {
        if (numericTolerance < 0.0) {
            throw new IllegalArgumentException("numericTolerance must be >= 0");
        }
        businessKeys = copyBusinessKeys(businessKeys);
        ignoredAttributes = ignoredAttributes != null ? Set.copyOf(ignoredAttributes) : Set.of();
        expectedLossReasonCodes = expectedLossReasonCodes != null ? Set.copyOf(expectedLossReasonCodes) : Set.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> businessKeyFor(String objectTag) {
        if (objectTag == null) {
            return List.of();
        }
        List<String> exact = businessKeys.get(objectTag);
        if (exact != null) {
            return exact;
        }
        String shortName = shortName(objectTag);
        List<String> shortMatch = businessKeys.get(shortName);
        if (shortMatch != null) {
            return shortMatch;
        }
        for (var entry : businessKeys.entrySet()) {
            if (objectTag.endsWith("." + entry.getKey())) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    public boolean ignores(String path) {
        if (path == null || ignoredAttributes.isEmpty()) {
            return false;
        }
        for (String ignored : ignoredAttributes) {
            if (path.equals(ignored) || path.endsWith("." + ignored)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<String>> copyBusinessKeys(Map<String, List<String>> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (var entry : input.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static String shortName(String tag) {
        int idx = tag.lastIndexOf('.');
        return idx >= 0 ? tag.substring(idx + 1) : tag;
    }

    public static final class Builder {
        private double numericTolerance = DEFAULT_NUMERIC_TOLERANCE;
        private final Map<String, List<String>> businessKeys = new LinkedHashMap<>();
        private final Set<String> ignoredAttributes = new LinkedHashSet<>();
        private final Set<String> expectedLossReasonCodes = new LinkedHashSet<>();

        public Builder numericTolerance(double numericTolerance) {
            this.numericTolerance = numericTolerance;
            return this;
        }

        public Builder businessKey(String objectTagOrSuffix, String... attributes) {
            this.businessKeys.put(objectTagOrSuffix, List.of(attributes));
            return this;
        }

        public Builder businessKey(String objectTagOrSuffix, List<String> attributes) {
            this.businessKeys.put(objectTagOrSuffix, new ArrayList<>(attributes));
            return this;
        }

        public Builder ignore(String attributeOrPath) {
            this.ignoredAttributes.add(attributeOrPath);
            return this;
        }

        public Builder expectedLossReasonCode(String reasonCode) {
            this.expectedLossReasonCodes.add(reasonCode);
            return this;
        }

        public ComparisonProfile build() {
            return new ComparisonProfile(numericTolerance, businessKeys, ignoredAttributes, expectedLossReasonCodes);
        }
    }
}
