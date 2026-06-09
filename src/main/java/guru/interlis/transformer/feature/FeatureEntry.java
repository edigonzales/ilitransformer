package guru.interlis.transformer.feature;

import java.util.Collections;
import java.util.List;

public record FeatureEntry(
        String feature,
        String phase,
        FeatureStatus status,
        String description,
        List<String> testReferences
) {
    public FeatureEntry {
        testReferences = Collections.unmodifiableList(testReferences);
    }

    public static FeatureEntry of(String feature, String phase, FeatureStatus status,
                                  String description, String... testReferences) {
        return new FeatureEntry(feature, phase, status, description,
                List.of(testReferences));
    }
}
