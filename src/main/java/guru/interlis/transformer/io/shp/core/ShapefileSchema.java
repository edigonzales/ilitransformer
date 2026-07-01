package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.util.List;

/**
 * Describes the structure of a Shapefile dataset to be written: the geometry {@link ShapeType} of
 * the {@code .shp} file and the ordered list of {@code .dbf} fields.
 *
 * <p>The order of {@link #fields()} defines the order in which attribute values must be supplied to
 * {@link ShapefileDatasetWriter#write(com.vividsolutions.jts.geom.Geometry, Object[])}.
 *
 * <p>This is a low-level, format-only description. It intentionally lives in the {@code core}
 * package and knows nothing about INTERLIS, IOX or the mapping layer.
 */
public record ShapefileSchema(ShapeType shapeType, List<DbfField> fields) {

    public ShapefileSchema {
        if (shapeType == null) {
            throw new IllegalArgumentException("Shapefile schema shape type must not be null");
        }
        fields = List.copyOf(fields);
    }

    /**
     * Validates that this schema describes a geometry type the writer can produce. The writer
     * supports NullShape, Point, MultiPoint, PolyLine and Polygon.
     */
    public void validateWritable() throws ShapefileMappingException {
        switch (shapeType) {
            case NULL, POINT, MULTIPOINT, POLYLINE, POLYGON -> {
                // supported
            }
            default ->
                throw new ShapefileMappingException("Shapefile writer cannot write shape type " + shapeType + " ("
                        + shapeType.code()
                        + "). Supported: NULL (0), POINT (1), MULTIPOINT (8), POLYLINE (3), POLYGON (5).");
        }
    }
}
