package guru.interlis.transformer.feature;

import java.util.Collections;
import java.util.List;

final class FeatureEntryYaml {
    public String feature;
    public String phase;
    public FeatureStatus status;
    public String description;
    public List<String> testReferences;

    FeatureEntryYaml() {}

    FeatureEntry toFeatureEntry() {
        List<String> refs = testReferences != null
                ? Collections.unmodifiableList(testReferences)
                : List.of();
        return new FeatureEntry(feature, phase, status, description, refs);
    }
}
