package guru.interlis.transformer.io.shp.geom;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Jts2iox;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import org.junit.jupiter.api.Test;

class IoxToJtsGeometryTest {

    private final GeometryFactory gf = new GeometryFactory();
    private final IoxToJtsGeometry converter = new IoxToJtsGeometry();

    @Test
    void convertsCoordToPoint() throws Exception {
        IomObject coord = Jts2iox.JTS2coord(new Coordinate(2600000.0, 1200000.0));

        Geometry result = converter.convert(coord);

        assertThat(result).isInstanceOf(Point.class);
        Coordinate c = result.getCoordinate();
        assertThat(c.x).isEqualTo(2600000.0);
        assertThat(c.y).isEqualTo(1200000.0);
    }

    @Test
    void convertsPolylineToLineString() throws Exception {
        LineString line = gf.createLineString(new Coordinate[] {
            new Coordinate(2600000, 1200000), new Coordinate(2600100, 1200100), new Coordinate(2600200, 1200000)
        });
        IomObject polyline = Jts2iox.JTS2polyline(line);

        Geometry result = converter.convert(polyline);

        assertThat(result).isInstanceOf(LineString.class);
        assertThat(result.getNumPoints()).isEqualTo(3);
    }

    @Test
    void convertsMultiPolylineToMultiLineString() throws Exception {
        LineString a = gf.createLineString(new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1)});
        LineString b = gf.createLineString(new Coordinate[] {new Coordinate(2, 2), new Coordinate(3, 3)});
        MultiLineString mls = gf.createMultiLineString(new LineString[] {a, b});
        IomObject multipolyline = Jts2iox.JTS2multipolyline(mls);

        Geometry result = converter.convert(multipolyline);

        assertThat(result).isInstanceOf(MultiLineString.class);
        assertThat(result.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void convertsSurfaceToPolygonal() throws Exception {
        Polygon polygon = square(0, 0, 10);
        IomObject surface = Jts2iox.JTS2surface(polygon);

        Geometry result = converter.convert(surface);

        // Jts2iox.JTS2surface wraps a single shell as a MULTISURFACE, which round-trips to a MultiPolygon;
        // the Shapefile encoder maps both Polygon and MultiPolygon to shape type Polygon.
        assertThat(result).isInstanceOf(com.vividsolutions.jts.geom.Polygonal.class);
        assertThat(result.getArea()).isCloseTo(100.0, org.assertj.core.api.Assertions.within(1e-6));
    }

    @Test
    void convertsMultiSurfaceToMultiPolygon() throws Exception {
        MultiPolygon mp = gf.createMultiPolygon(new Polygon[] {square(0, 0, 10), square(100, 100, 5)});
        IomObject multisurface = Jts2iox.JTS2multisurface(mp);

        Geometry result = converter.convert(multisurface);

        assertThat(result).isInstanceOf(MultiPolygon.class);
        assertThat(result.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void convertsMultiCoordToMultiPoint() throws Exception {
        IomObject multicoord = Jts2iox.JTS2multicoord(
                new Coordinate[] {new Coordinate(2600000, 1200000), new Coordinate(2600100, 1200100)});

        Geometry result = converter.convert(multicoord);

        assertThat(result).isInstanceOf(MultiPoint.class);
        assertThat(result.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void nullGeometryReturnsNull() throws Exception {
        assertThat(converter.convert(null)).isNull();
    }

    private Polygon square(double x, double y, double size) {
        LinearRing ring = gf.createLinearRing(new Coordinate[] {
            new Coordinate(x, y),
            new Coordinate(x + size, y),
            new Coordinate(x + size, y + size),
            new Coordinate(x, y + size),
            new Coordinate(x, y)
        });
        return gf.createPolygon(ring, null);
    }
}
