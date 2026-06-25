package guru.interlis.transformer.io.shp.geom;

import java.util.Locale;

public enum GeometryKind {
    COORD,
    POLYLINE,
    SURFACE;

    public static GeometryKind fromOptionValue(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "coord" -> COORD;
            case "polyline" -> POLYLINE;
            case "surface" -> SURFACE;
            default ->
                throw new IllegalArgumentException(
                        "Unsupported geometry type '" + value + "'. Supported: coord, polyline, surface.");
        };
    }
}
