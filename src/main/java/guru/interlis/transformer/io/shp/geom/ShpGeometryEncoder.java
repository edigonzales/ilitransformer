package guru.interlis.transformer.io.shp.geom;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.core.Bounds;
import guru.interlis.transformer.io.shp.core.EncodedShape;
import guru.interlis.transformer.io.shp.core.EndianByteBuffer;
import guru.interlis.transformer.io.shp.core.ShapeType;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Encodes a {@code com.vividsolutions.jts} geometry into a single {@link EncodedShape}, i.e. the
 * little-endian content of one Shapefile record (including the leading shape-type integer).
 *
 * <p>This is the symmetric counterpart of {@code ShpGeometryDecoder}. It keeps all JTS encoding
 * logic out of the {@code core} package, exactly like the decoder keeps JTS decoding logic here.
 *
 * <p>MVP mapping:
 *
 * <ul>
 *   <li>{@code null}/empty geometry &rarr; {@code NULL} shape (type 0)
 *   <li>{@link Point} &rarr; Point (type 1)
 *   <li>{@link LineString}/{@link MultiLineString} &rarr; PolyLine (type 3)
 *   <li>{@link Polygon}/{@link MultiPolygon} &rarr; Polygon (type 5)
 * </ul>
 *
 * <p>Polygon ring orientation is normalised so the result round-trips through {@code
 * ShpGeometryDecoder}: shells are emitted clockwise (signed area &lt; 0) and holes counter-clockwise.
 */
public final class ShpGeometryEncoder {

    private static final int SHAPE_TYPE_BYTES = 4;
    private static final int BBOX_BYTES = 32;
    private static final int POINT_XY_BYTES = 16;

    public EncodedShape encode(Geometry geometry) throws ShapefileMappingException {
        if (geometry == null || geometry.isEmpty()) {
            return encodeNull();
        }
        if (geometry instanceof Point point) {
            return encodePoint(point);
        }
        if (geometry instanceof MultiPoint multiPoint) {
            return encodeMultiPoint(multiPoint);
        }
        if (geometry instanceof LineString || geometry instanceof MultiLineString) {
            return encodePolyLine(geometry);
        }
        if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
            return encodePolygon(geometry);
        }
        throw new ShapefileMappingException("Cannot encode geometry of type '"
                + geometry.getGeometryType() + "'. Supported: Point, MultiPoint, LineString, MultiLineString, "
                + "Polygon, MultiPolygon.");
    }

    private EncodedShape encodeNull() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(SHAPE_TYPE_BYTES);
        buf.putLittleInt(ShapeType.NULL.code());
        buf.flip();
        return new EncodedShape(buf.buffer(), SHAPE_TYPE_BYTES, null);
    }

    private EncodedShape encodePoint(Point point) {
        Coordinate c = point.getCoordinate();
        int size = SHAPE_TYPE_BYTES + POINT_XY_BYTES;
        EndianByteBuffer buf = EndianByteBuffer.allocate(size);
        buf.putLittleInt(ShapeType.POINT.code());
        buf.putLittleDouble(c.x);
        buf.putLittleDouble(c.y);
        buf.flip();
        return new EncodedShape(buf.buffer(), size, new Bounds(c.x, c.y, c.x, c.y));
    }

    private EncodedShape encodeMultiPoint(MultiPoint multiPoint) {
        Coordinate[] points = multiPoint.getCoordinates();
        int numPoints = points.length;
        int size = SHAPE_TYPE_BYTES + BBOX_BYTES + 4 + numPoints * POINT_XY_BYTES;
        EndianByteBuffer buf = EndianByteBuffer.allocate(size);

        Bounds bounds = computeBounds(List.<Coordinate[]>of(points));

        buf.putLittleInt(ShapeType.MULTIPOINT.code());
        buf.putLittleDouble(bounds.xmin());
        buf.putLittleDouble(bounds.ymin());
        buf.putLittleDouble(bounds.xmax());
        buf.putLittleDouble(bounds.ymax());
        buf.putLittleInt(numPoints);
        for (Coordinate c : points) {
            buf.putLittleDouble(c.x);
            buf.putLittleDouble(c.y);
        }
        buf.flip();
        return new EncodedShape(buf.buffer(), size, bounds);
    }

    private EncodedShape encodePolyLine(Geometry geometry) {
        List<Coordinate[]> parts = new ArrayList<>();
        if (geometry instanceof LineString line) {
            parts.add(line.getCoordinates());
        } else {
            MultiLineString multi = (MultiLineString) geometry;
            for (int i = 0; i < multi.getNumGeometries(); i++) {
                parts.add(multi.getGeometryN(i).getCoordinates());
            }
        }
        return encodeParts(ShapeType.POLYLINE, parts);
    }

    private EncodedShape encodePolygon(Geometry geometry) {
        List<Coordinate[]> parts = new ArrayList<>();
        if (geometry instanceof Polygon polygon) {
            addPolygonRings(polygon, parts);
        } else {
            MultiPolygon multi = (MultiPolygon) geometry;
            for (int i = 0; i < multi.getNumGeometries(); i++) {
                addPolygonRings((Polygon) multi.getGeometryN(i), parts);
            }
        }
        return encodeParts(ShapeType.POLYGON, parts);
    }

    private void addPolygonRings(Polygon polygon, List<Coordinate[]> parts) {
        parts.add(orient(polygon.getExteriorRing().getCoordinates(), true));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            parts.add(orient(polygon.getInteriorRingN(i).getCoordinates(), false));
        }
    }

    /**
     * Returns the ring oriented for Shapefile output: shells clockwise (signed area &lt; 0), holes
     * counter-clockwise (signed area &gt; 0), matching the convention used by {@code
     * ShpGeometryDecoder}.
     */
    private Coordinate[] orient(Coordinate[] ring, boolean shell) {
        double area = signedArea(ring);
        boolean isClockwise = area < 0.0;
        if (shell != isClockwise) {
            return reverse(ring);
        }
        return ring;
    }

    private Coordinate[] reverse(Coordinate[] ring) {
        Coordinate[] out = new Coordinate[ring.length];
        for (int i = 0; i < ring.length; i++) {
            out[i] = ring[ring.length - 1 - i];
        }
        return out;
    }

    private EncodedShape encodeParts(ShapeType shapeType, List<Coordinate[]> parts) {
        int numParts = parts.size();
        int numPoints = 0;
        for (Coordinate[] part : parts) {
            numPoints += part.length;
        }

        int size = SHAPE_TYPE_BYTES + BBOX_BYTES + 4 + 4 + numParts * 4 + numPoints * POINT_XY_BYTES;
        EndianByteBuffer buf = EndianByteBuffer.allocate(size);

        Bounds bounds = computeBounds(parts);

        buf.putLittleInt(shapeType.code());
        buf.putLittleDouble(bounds.xmin());
        buf.putLittleDouble(bounds.ymin());
        buf.putLittleDouble(bounds.xmax());
        buf.putLittleDouble(bounds.ymax());
        buf.putLittleInt(numParts);
        buf.putLittleInt(numPoints);

        int start = 0;
        for (Coordinate[] part : parts) {
            buf.putLittleInt(start);
            start += part.length;
        }
        for (Coordinate[] part : parts) {
            for (Coordinate c : part) {
                buf.putLittleDouble(c.x);
                buf.putLittleDouble(c.y);
            }
        }

        buf.flip();
        return new EncodedShape(buf.buffer(), size, bounds);
    }

    private Bounds computeBounds(List<Coordinate[]> parts) {
        double xmin = Double.POSITIVE_INFINITY;
        double ymin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymax = Double.NEGATIVE_INFINITY;
        for (Coordinate[] part : parts) {
            for (Coordinate c : part) {
                xmin = Math.min(xmin, c.x);
                ymin = Math.min(ymin, c.y);
                xmax = Math.max(xmax, c.x);
                ymax = Math.max(ymax, c.y);
            }
        }
        return new Bounds(xmin, ymin, xmax, ymax);
    }

    static double signedArea(Coordinate[] coords) {
        double area = 0.0;
        int n = coords.length;
        for (int i = 0; i < n - 1; i++) {
            area += coords[i].x * coords[i + 1].y - coords[i + 1].x * coords[i].y;
        }
        return area / 2.0;
    }
}
