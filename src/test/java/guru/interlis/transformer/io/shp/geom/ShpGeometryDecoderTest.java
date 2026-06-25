package guru.interlis.transformer.io.shp.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.core.Bounds;
import guru.interlis.transformer.io.shp.core.EndianByteBuffer;
import guru.interlis.transformer.io.shp.core.ShapeRecord;
import guru.interlis.transformer.io.shp.core.ShapeType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import org.junit.jupiter.api.Test;

class ShpGeometryDecoderTest {

    private final ShpGeometryDecoder decoder = new ShpGeometryDecoder();

    @Test
    void decodesPoint() throws Exception {
        ShapeRecord record = buildRecord(ShapeType.POINT, buf -> {
            buf.putLittleDouble(7.5);
            buf.putLittleDouble(47.2);
        });

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(Point.class);
        Point point = (Point) geom;
        assertThat(point.getX()).isEqualTo(7.5);
        assertThat(point.getY()).isEqualTo(47.2);
    }

    @Test
    void decodesNullShapeAsNull() throws Exception {
        ShapeRecord record = buildRecord(ShapeType.NULL, buf -> {});

        Geometry geom = decoder.decode(record);
        assertThat(geom).isNull();
    }

    @Test
    void decodesSinglePartPolyLine() throws Exception {
        ShapeRecord record = buildPolyLineRecord(new double[][] {{0.0, 0.0}, {10.0, 0.0}, {10.0, 10.0}}, new int[] {0});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(LineString.class);
        LineString ls = (LineString) geom;
        assertThat(ls.getNumPoints()).isEqualTo(3);
        assertThat(ls.getCoordinateN(0)).isEqualTo(new Coordinate(0.0, 0.0));
        assertThat(ls.getCoordinateN(1)).isEqualTo(new Coordinate(10.0, 0.0));
        assertThat(ls.getCoordinateN(2)).isEqualTo(new Coordinate(10.0, 10.0));
    }

    @Test
    void decodesMultiPartPolyLine() throws Exception {
        ShapeRecord record = buildPolyLineRecord(
                new double[][] {{0.0, 0.0}, {5.0, 5.0}, {2.0, 3.0}, {6.0, 6.0}, {8.0, 8.0}}, new int[] {0, 3});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(MultiLineString.class);
        MultiLineString mls = (MultiLineString) geom;
        assertThat(mls.getNumGeometries()).isEqualTo(2);
        assertThat(mls.getGeometryN(0).getNumPoints()).isEqualTo(3);
        assertThat(mls.getGeometryN(1).getNumPoints()).isEqualTo(2);
    }

    @Test
    void decodesPolygonSingleShell() throws Exception {
        ShapeRecord record = buildPolygonRecord(
                new double[][] {{0.0, 0.0}, {0.0, 10.0}, {10.0, 10.0}, {10.0, 0.0}, {0.0, 0.0}}, new int[] {0});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(Polygon.class);
        Polygon poly = (Polygon) geom;
        assertThat(poly.getExteriorRing().getNumPoints()).isEqualTo(5);
        assertThat(poly.getNumInteriorRing()).isEqualTo(0);
    }

    @Test
    void decodesPolygonWithHole() throws Exception {
        ShapeRecord record = buildPolygonRecord(
                new double[][] {
                    {0.0, 0.0}, {0.0, 10.0}, {10.0, 10.0}, {10.0, 0.0}, {0.0, 0.0},
                    {4.0, 4.0}, {6.0, 4.0}, {6.0, 6.0}, {4.0, 6.0}, {4.0, 4.0}
                },
                new int[] {0, 5});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(Polygon.class);
        Polygon poly = (Polygon) geom;
        assertThat(poly.getExteriorRing().getNumPoints()).isEqualTo(5);
        assertThat(poly.getNumInteriorRing()).isEqualTo(1);
        assertThat(poly.getInteriorRingN(0).getNumPoints()).isEqualTo(5);
    }

    @Test
    void decodesMultiPolygon() throws Exception {
        ShapeRecord record = buildPolygonRecord(
                new double[][] {
                    {0.0, 0.0}, {0.0, 5.0}, {5.0, 5.0}, {5.0, 0.0}, {0.0, 0.0},
                    {10.0, 10.0}, {10.0, 15.0}, {15.0, 15.0}, {15.0, 10.0}, {10.0, 10.0}
                },
                new int[] {0, 5});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(MultiPolygon.class);
        MultiPolygon mp = (MultiPolygon) geom;
        assertThat(mp.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void multiPolygonWithHolesInFirstShell() throws Exception {
        ShapeRecord record = buildPolygonRecord(
                new double[][] {
                    {0.0, 0.0}, {0.0, 10.0}, {10.0, 10.0}, {10.0, 0.0}, {0.0, 0.0},
                    {4.0, 4.0}, {6.0, 4.0}, {6.0, 6.0}, {4.0, 6.0}, {4.0, 4.0},
                    {2.0, 2.0}, {3.0, 2.0}, {3.0, 3.0}, {2.0, 3.0}, {2.0, 2.0},
                    {12.0, 12.0}, {12.0, 15.0}, {15.0, 15.0}, {15.0, 12.0}, {12.0, 12.0}
                },
                new int[] {0, 5, 10, 15});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(MultiPolygon.class);
        MultiPolygon mp = (MultiPolygon) geom;
        assertThat(mp.getNumGeometries()).isEqualTo(2);
        Polygon first = (Polygon) mp.getGeometryN(0);
        assertThat(first.getNumInteriorRing()).isEqualTo(2);
        Polygon second = (Polygon) mp.getGeometryN(1);
        assertThat(second.getNumInteriorRing()).isEqualTo(0);
    }

    @Test
    void rejectsUnclosedRing() throws Exception {
        ShapeRecord record =
                buildPolygonRecord(new double[][] {{0.0, 0.0}, {10.0, 0.0}, {10.0, 10.0}, {0.0, 9.0}}, new int[] {0});

        assertThatThrownBy(() -> decoder.decode(record))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("not closed");
    }

    @Test
    void rejectsRingWithTooFewPoints() throws Exception {
        ShapeRecord record = buildPolygonRecord(new double[][] {{0.0, 0.0}, {1.0, 1.0}, {0.0, 0.0}}, new int[] {0});

        assertThatThrownBy(() -> decoder.decode(record))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("need at least 4");
    }

    @Test
    void rejectsHoleNotContainedByAnyShell() throws Exception {
        ShapeRecord record = buildPolygonRecord(
                new double[][] {
                    {0.0, 0.0}, {0.0, 5.0}, {5.0, 5.0}, {5.0, 0.0}, {0.0, 0.0},
                    {10.0, 10.0}, {12.0, 10.0}, {12.0, 12.0}, {10.0, 12.0}, {10.0, 10.0}
                },
                new int[] {0, 5});

        assertThatThrownBy(() -> decoder.decode(record))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("not contained by any shell");
    }

    @Test
    void decodesPolygonWithHoleBeforeShellInRingOrder() throws Exception {
        ShapeRecord record = buildPolygonRecord(
                new double[][] {
                    {4.0, 4.0}, {6.0, 4.0}, {6.0, 6.0}, {4.0, 6.0}, {4.0, 4.0},
                    {0.0, 0.0}, {0.0, 10.0}, {10.0, 10.0}, {10.0, 0.0}, {0.0, 0.0}
                },
                new int[] {0, 5});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(Polygon.class);
        Polygon poly = (Polygon) geom;
        assertThat(poly.getExteriorRing().getNumPoints()).isEqualTo(5);
        assertThat(poly.getNumInteriorRing()).isEqualTo(1);
    }

    @Test
    void decodesHoleAssignedToSmallestContainingShell() throws Exception {
        ShapeRecord record = buildPolygonRecord(
                new double[][] {
                    {0.0, 0.0}, {0.0, 20.0}, {20.0, 20.0}, {20.0, 0.0}, {0.0, 0.0},
                    {2.0, 2.0}, {2.0, 10.0}, {10.0, 10.0}, {10.0, 2.0}, {2.0, 2.0},
                    {4.0, 4.0}, {6.0, 4.0}, {6.0, 6.0}, {4.0, 6.0}, {4.0, 4.0}
                },
                new int[] {0, 5, 10});

        Geometry geom = decoder.decode(record);

        assertThat(geom).isInstanceOf(MultiPolygon.class);
        MultiPolygon mp = (MultiPolygon) geom;
        assertThat(mp.getNumGeometries()).isEqualTo(2);
        Polygon first = (Polygon) mp.getGeometryN(0);
        assertThat(first.getNumInteriorRing()).isEqualTo(0);
        Polygon second = (Polygon) mp.getGeometryN(1);
        assertThat(second.getNumInteriorRing()).isEqualTo(1);
    }

    @Test
    void signedAreaPositiveForCounterClockwiseRing() {
        Coordinate[] ccw = {
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        };
        com.vividsolutions.jts.geom.GeometryFactory gf = new com.vividsolutions.jts.geom.GeometryFactory();
        com.vividsolutions.jts.geom.LinearRing ring = gf.createLinearRing(ccw);
        double area = ShpGeometryDecoder.signedArea(ring);
        assertThat(area).isPositive();
    }

    @Test
    void signedAreaNegativeForClockwiseRing() {
        Coordinate[] cw = {
            new Coordinate(0, 0),
            new Coordinate(0, 10),
            new Coordinate(10, 10),
            new Coordinate(10, 0),
            new Coordinate(0, 0)
        };
        com.vividsolutions.jts.geom.GeometryFactory gf = new com.vividsolutions.jts.geom.GeometryFactory();
        com.vividsolutions.jts.geom.LinearRing ring = gf.createLinearRing(cw);
        double area = ShpGeometryDecoder.signedArea(ring);
        assertThat(area).isNegative();
    }

    private ShapeRecord buildRecord(ShapeType shapeType, ContentWriter writer) {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(shapeType.code());
        EndianByteBuffer ebb = EndianByteBuffer.wrap(buf);
        writer.writeContent(ebb);
        int position = buf.position();
        buf.flip();
        ByteBuffer content = ByteBuffer.allocate(position);
        content.put(buf);
        content.flip();
        return new ShapeRecord(1, shapeType, content, new Bounds(0, 0, 0, 0));
    }

    private ShapeRecord buildPolyLineRecord(double[][] points, int[] parts) {
        return buildRecord(ShapeType.POLYLINE, buf -> {
            for (int i = 0; i < 4; i++) {
                buf.putLittleDouble(0.0);
            }
            buf.putLittleInt(parts.length);
            buf.putLittleInt(points.length);
            for (int part : parts) {
                buf.putLittleInt(part);
            }
            for (double[] pt : points) {
                buf.putLittleDouble(pt[0]);
                buf.putLittleDouble(pt[1]);
            }
        });
    }

    private ShapeRecord buildPolygonRecord(double[][] points, int[] parts) {
        return buildRecord(ShapeType.POLYGON, buf -> {
            for (int i = 0; i < 4; i++) {
                buf.putLittleDouble(0.0);
            }
            buf.putLittleInt(parts.length);
            buf.putLittleInt(points.length);
            for (int part : parts) {
                buf.putLittleInt(part);
            }
            for (double[] pt : points) {
                buf.putLittleDouble(pt[0]);
                buf.putLittleDouble(pt[1]);
            }
        });
    }

    @FunctionalInterface
    private interface ContentWriter {
        void writeContent(EndianByteBuffer buf);
    }
}
