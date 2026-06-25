package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.geom.GeometryKind;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum ShapeType {
    NULL(0),
    POINT(1),
    POLYLINE(3),
    POLYGON(5),
    MULTIPOINT(8),
    POINT_Z(11),
    POLYLINE_Z(13),
    POLYGON_Z(15),
    MULTIPOINT_Z(18),
    POINT_M(21),
    POLYLINE_M(23),
    POLYGON_M(25),
    MULTIPOINT_M(28),
    MULTIPATCH(31);

    private final int code;

    ShapeType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ShapeType fromCode(int code) throws ShapefileMappingException {
        for (ShapeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        String known = Arrays.stream(values()).map(t -> t.code + "=" + t.name()).collect(Collectors.joining(", "));
        throw new ShapefileMappingException(
                "Unknown or unsupported shape type code: " + code + ". Known types: [" + known + "]");
    }

    public boolean isSupported2dMvp() {
        return this == NULL || this == POINT || this == POLYLINE || this == POLYGON || this == MULTIPOINT;
    }

    public GeometryKind defaultGeometryKind() throws ShapefileMappingException {
        return switch (this) {
            case POINT -> GeometryKind.COORD;
            case MULTIPOINT -> GeometryKind.MULTICOORD;
            case POLYLINE -> GeometryKind.POLYLINE;
            case POLYGON -> GeometryKind.SURFACE;
            default ->
                throw new ShapefileMappingException("Shape type " + this
                        + " has no default geometry kind. Only POINT, MULTIPOINT, POLYLINE and POLYGON are supported.");
        };
    }
}
