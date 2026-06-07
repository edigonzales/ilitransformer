package guru.interlis.transformer.geometry;

import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.PolylineValue;
import guru.interlis.transformer.expr.SurfaceValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class NoOpGeometryAdapterTest {

    private final NoOpGeometryAdapter adapter = new NoOpGeometryAdapter();

    @Test
    void normalizeCoordFromIomObject() {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", "2600000.0");
        coord.setattrvalue("C2", "1200000.0");

        Value result = adapter.normalize(coord, TypeInfo.COORD);
        assertThat(result).isInstanceOf(CoordValue.class);
        CoordValue cv = (CoordValue) result;
        assertThat(cv.x()).isEqualTo(2600000.0);
        assertThat(cv.y()).isEqualTo(1200000.0);
    }

    @Test
    void normalizeCoordFromStringValue() {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("value", "2600000.000 1200000.000");

        Value result = adapter.normalize(coord, TypeInfo.COORD);
        assertThat(result).isInstanceOf(CoordValue.class);
        CoordValue cv = (CoordValue) result;
        assertThat(cv.x()).isEqualTo(2600000.0);
        assertThat(cv.y()).isEqualTo(1200000.0);
    }

    @Test
    void denormalizeCoordToIomObject() {
        CoordValue cv = new CoordValue(2600000.0, 1200000.0);
        var result = adapter.denormalize(cv, TypeInfo.COORD);
        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("COORD");
        assertThat(result.getattrvalue("C1")).isEqualTo("2600000.0");
        assertThat(result.getattrvalue("C2")).isEqualTo("1200000.0");
    }

    @Test
    void normalizePolylineFromIomObject() {
        Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
        Iom_jObject c1 = new Iom_jObject("COORD", null);
        c1.setattrvalue("C1", "2600000.0");
        c1.setattrvalue("C2", "1200000.0");
        polyline.addattrobj("coord", c1);
        Iom_jObject c2 = new Iom_jObject("COORD", null);
        c2.setattrvalue("C1", "2600100.0");
        c2.setattrvalue("C2", "1200100.0");
        polyline.addattrobj("coord", c2);

        Value result = adapter.normalize(polyline, TypeInfo.POLYLINE);
        assertThat(result).isInstanceOf(PolylineValue.class);
        PolylineValue pv = (PolylineValue) result;
        assertThat(pv.points()).hasSize(2);
        assertThat(pv.points().get(0).x()).isEqualTo(2600000.0);
    }

    @Test
    void denormalizePolylineRoundtrip() {
        PolylineValue pv = new PolylineValue(List.of(
                new CoordValue(2600000.0, 1200000.0),
                new CoordValue(2600100.0, 1200100.0)));
        var result = adapter.denormalize(pv, TypeInfo.POLYLINE);
        assertThat(result).isNotNull();
        assertThat(result.getattrvaluecount("coord")).isEqualTo(2);

        Value back = adapter.normalize(result, TypeInfo.POLYLINE);
        assertThat(back).isInstanceOf(PolylineValue.class);
        assertThat(((PolylineValue) back).points()).hasSize(2);
    }

    @Test
    void normalizeSurfaceFromIomObject() {
        Iom_jObject surface = new Iom_jObject("SURFACE", null);
        Iom_jObject boundary = new Iom_jObject("boundary", null);
        Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
        Iom_jObject c1 = new Iom_jObject("COORD", null);
        c1.setattrvalue("C1", "0.0");
        c1.setattrvalue("C2", "0.0");
        polyline.addattrobj("coord", c1);
        boundary.addattrobj("polyline", polyline);
        surface.addattrobj("boundary", boundary);

        Value result = adapter.normalize(surface, TypeInfo.SURFACE);
        assertThat(result).isInstanceOf(SurfaceValue.class);
        SurfaceValue sv = (SurfaceValue) result;
        assertThat(sv.rings()).hasSize(1);
    }

    @Test
    void transformPassthrough() {
        CoordValue cv = new CoordValue(1.0, 2.0);
        Value result = adapter.transform(cv, GeometryAdapter.GeometryOperation.PASSTHROUGH);
        assertThat(result).isSameAs(cv);
    }

    @Test
    void normalizeNullReturnsNull() {
        assertThat(adapter.normalize(null, TypeInfo.COORD).isNull()).isTrue();
    }
}
