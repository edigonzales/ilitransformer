package guru.interlis.transformer.geometry;

import java.util.Collections;
import java.util.List;

public record GeometryCompatibility(boolean compatible, List<String> incompatibilities) {

    public static GeometryCompatibility success() {
        return new GeometryCompatibility(true, Collections.emptyList());
    }

    public static GeometryCompatibility failure(String reason) {
        return new GeometryCompatibility(false, List.of(reason));
    }

    public static GeometryCompatibility failure(List<String> reasons) {
        return new GeometryCompatibility(false, List.copyOf(reasons));
    }
}
