package guru.interlis.transformer.io.shp.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.core.Bounds;
import guru.interlis.transformer.io.shp.core.EncodedShape;
import guru.interlis.transformer.io.shp.core.EndianByteBuffer;
import guru.interlis.transformer.io.shp.core.ShapeRecord;
import guru.interlis.transformer.io.shp.core.ShapeType;

import java.nio.ByteBuffer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import org.junit.jupiter.api.Test;

class ShpGeometryEncoderTest {

    private final ShpGeometryEncoder encoder = new ShpGeometryEncoder();
    private final ShpGeometryDecoder decoder = new ShpGeometryDecoder();
    private final GeometryFactory gf = new GeometryFactory();

    @Test
    void encodesNullGeometryAsNullShape() throws Exception {
        EncodedShape encoded = encoder.encode(null);
        assertThat(encoded.contentLengthBytes()).isEqualTo(4);
        assertThat(encoded.bounds()).isNull();
        assertThat(decode(encoded)).isNull();
    }

    @Test
    void encodesPointRoundtrips() throws Exception {
        Geometry point = gf.createPoint(new Coordinate(7.5, 47.2));
        EncodedShape encoded = encoder.encode(point);

        assertThat(encoded.contentLengthBytes()).isEqualTo(20);
        assertThat(encoded.bounds()).isEqualTo(new Bounds(7.5, 47.2, 7.5, 47.2));

        Geometry decoded = decode(encoded);
        assertThat(decoded.equalsTopo(point)).isTrue();
    }

    @Test
    void encodesLineStringAsSinglePartPolyLine() throws Exception {
        Geometry line = gf.createLineString(
                new Coordinate[] {new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(10, 10)});
        EncodedShape encoded = encoder.encode(line);

        Geometry decoded = decode(encoded);
        assertThat(decoded.getGeometryType()).isEqualTo("LineString");
        assertThat(decoded.equalsTopo(line)).isTrue();
    }

    @Test
    void encodesMultiLineStringAsMultiPartPolyLine() throws Exception {
        Geometry multi = gf.createMultiLineString(new com.vividsolutions.jts.geom.LineString[] {
            gf.createLineString(new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1)}),
            gf.createLineString(new Coordinate[] {new Coordinate(5, 5), new Coordinate(6, 6)})
        });
        EncodedShape encoded = encoder.encode(multi);

        Geometry decoded = decode(encoded);
        assertThat(decoded.getGeometryType()).isEqualTo("MultiLineString");
        assertThat(decoded.getNumGeometries()).isEqualTo(2);
        assertThat(decoded.equalsTopo(multi)).isTrue();
    }

    @Test
    void encodesPolygonShellRoundtrips() throws Exception {
        Polygon polygon = gf.createPolygon(closedRing(new double[][] {{0, 0}, {10, 0}, {10, 10}, {0, 10}, {0, 0}}));
        EncodedShape encoded = encoder.encode(polygon);

        Geometry decoded = decode(encoded);
        assertThat(decoded.getGeometryType()).isEqualTo("Polygon");
        assertThat(decoded.equalsTopo(polygon)).isTrue();
        assertThat(decoded.getArea()).isEqualTo(100.0);
    }

    @Test
    void encodesPolygonWithHole() throws Exception {
        LinearRing shell = closedRing(new double[][] {{0, 0}, {10, 0}, {10, 10}, {0, 10}, {0, 0}});
        LinearRing hole = closedRing(new double[][] {{3, 3}, {3, 6}, {6, 6}, {6, 3}, {3, 3}});
        Polygon polygon = gf.createPolygon(shell, new LinearRing[] {hole});

        EncodedShape encoded = encoder.encode(polygon);
        Geometry decoded = decode(encoded);

        assertThat(decoded.getGeometryType()).isEqualTo("Polygon");
        assertThat(((Polygon) decoded).getNumInteriorRing()).isEqualTo(1);
        assertThat(decoded.getArea()).isEqualTo(100.0 - 9.0);
    }

    @Test
    void encodesMultiPolygon() throws Exception {
        Polygon a = gf.createPolygon(closedRing(new double[][] {{0, 0}, {2, 0}, {2, 2}, {0, 2}, {0, 0}}));
        Polygon b = gf.createPolygon(closedRing(new double[][] {{10, 10}, {12, 10}, {12, 12}, {10, 12}, {10, 10}}));
        Geometry multi = gf.createMultiPolygon(new Polygon[] {a, b});

        EncodedShape encoded = encoder.encode(multi);
        Geometry decoded = decode(encoded);

        assertThat(decoded.getGeometryType()).isEqualTo("MultiPolygon");
        assertThat(decoded.getNumGeometries()).isEqualTo(2);
        assertThat(decoded.getArea()).isEqualTo(8.0);
    }

    @Test
    void rejectsUnsupportedGeometry() {
        Geometry collection = gf.createGeometryCollection(new Geometry[] {gf.createPoint(new Coordinate(0, 0))});
        assertThatThrownBy(() -> encoder.encode(collection))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("Cannot encode geometry");
    }

    private Geometry decode(EncodedShape encoded) throws ShapefileMappingException {
        ByteBuffer typePeek = encoded.content().duplicate();
        typePeek.position(0);
        int code = EndianByteBuffer.wrap(typePeek).getLittleInt();
        ShapeType shapeType = ShapeType.fromCode(code);

        ByteBuffer content = encoded.content().duplicate();
        content.position(0);
        Bounds bounds = encoded.bounds() == null ? new Bounds(0, 0, 0, 0) : encoded.bounds();
        return decoder.decode(new ShapeRecord(1, shapeType, content, bounds));
    }

    private LinearRing closedRing(double[][] coords) {
        Coordinate[] cs = new Coordinate[coords.length];
        for (int i = 0; i < coords.length; i++) {
            cs[i] = new Coordinate(coords[i][0], coords[i][1]);
        }
        return gf.createLinearRing(cs);
    }
}
