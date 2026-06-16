package guru.interlis.transformer.geometry;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.GeometryObjectValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.support.TestGeometries;

import ch.interlis.iom_j.Iom_jObject;

import org.junit.jupiter.api.Test;

class IoxGeometryAdapterTest {

    private final IoxGeometryAdapter adapter = new IoxGeometryAdapter();

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
    }

    @Test
    void denormalizeCoordRoundtrip() {
        CoordValue cv = new CoordValue(2600000.0, 1200000.0);
        var result = adapter.denormalize(cv, TypeInfo.COORD);
        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("COORD");
        assertThat(result.getattrvalue("C1")).isEqualTo("2600000.0");
        assertThat(result.getattrvalue("C2")).isEqualTo("1200000.0");

        Value back = adapter.normalize(result, TypeInfo.COORD);
        assertThat(back).isInstanceOf(CoordValue.class);
    }

    @Test
    void normalizePolylineRoundtrip() {
        var polyline = TestGeometries.polyline(
                TestGeometries.coord(2600000.0, 1200000.0), TestGeometries.coord(2600100.0, 1200100.0));

        Value result = adapter.normalize(polyline, TypeInfo.POLYLINE);
        assertThat(result).isInstanceOf(GeometryObjectValue.class);
        GeometryObjectValue gov = (GeometryObjectValue) result;
        assertThat(gov.geometryType()).isEqualTo(TypeInfo.POLYLINE);

        // denormalize preserves the canonical geometry structure
        var denorm = adapter.denormalize(gov, TypeInfo.POLYLINE);
        assertThat(denorm).isNotNull();
        assertThat(denorm.getattrvaluecount("sequence")).isEqualTo(1);
        var dseq = denorm.getattrobj("sequence", 0);
        assertThat(dseq.getattrvaluecount("segment")).isEqualTo(2);
    }

    @Test
    void normalizeSurfaceFromIoxIliStructure() {
        var outer = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(0.0, 0.0), TestGeometries.coord(10.0, 0.0), TestGeometries.coord(10.0, 10.0)));

        Value result = adapter.normalize(outer, TypeInfo.SURFACE);
        assertThat(result).isInstanceOf(GeometryObjectValue.class);
        GeometryObjectValue sv = (GeometryObjectValue) result;
        assertThat(sv.geometryType()).isEqualTo(TypeInfo.SURFACE);
        assertThat(sv.geometryObject().getattrvaluecount("surface")).isEqualTo(1);
    }

    @Test
    void denormalizeGeometryObjectValuePreservesCanonicalSurface() {
        Iom_jObject surface = (Iom_jObject) TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(0.0, 0.0),
                TestGeometries.coord(10.0, 0.0),
                TestGeometries.coord(10.0, 10.0),
                TestGeometries.coord(0.0, 10.0),
                TestGeometries.coord(0.0, 0.0)));

        var result = adapter.denormalize(new GeometryObjectValue(TypeInfo.SURFACE, surface), TypeInfo.SURFACE);
        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("MULTISURFACE");

        assertThat(result.getattrvaluecount("surface")).isEqualTo(1);
        var innerSurface = result.getattrobj("surface", 0);
        assertThat(innerSurface).isNotNull();
        assertThat(innerSurface.getattrvaluecount("boundary")).isEqualTo(1);
        var boundary = innerSurface.getattrobj("boundary", 0);
        assertThat(boundary).isNotNull();
        assertThat(boundary.getattrvaluecount("polyline")).isEqualTo(1);
        var poly = boundary.getattrobj("polyline", 0);
        assertThat(poly).isNotNull();
        assertThat(poly.getattrvaluecount("sequence")).isEqualTo(1);
        var dseq = poly.getattrobj("sequence", 0);
        assertThat(dseq.getattrvaluecount("segment")).isEqualTo(5);
    }

    @Test
    void denormalizeGeometryObjectValueReturnsDeepCopy() {
        Iom_jObject surface = new Iom_jObject("SURFACE", null);
        Iom_jObject boundary = new Iom_jObject("boundary", null);
        boundary.addattrobj("polyline", new Iom_jObject("POLYLINE", null));
        surface.addattrobj("boundary", boundary);

        GeometryObjectValue value = new GeometryObjectValue(TypeInfo.SURFACE, surface);
        Iom_jObject copy = (Iom_jObject) adapter.denormalize(value, TypeInfo.SURFACE);

        assertThat(copy).isNotSameAs(surface);
        assertThat(copy.getattrvaluecount("boundary")).isEqualTo(1);
    }

    @Test
    void denormalizeAreaAcceptsCompatibleSurfaceGeometry() {
        var geometry = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(1.0, 1.0), TestGeometries.coord(2.0, 1.0), TestGeometries.coord(1.0, 1.0)));

        Iom_jObject result =
                (Iom_jObject) adapter.denormalize(new GeometryObjectValue(TypeInfo.SURFACE, geometry), TypeInfo.AREA);
        assertThat(result).isNotNull();
        assertThat(result.getattrvaluecount("surface")).isEqualTo(1);
    }

    @Test
    void normalizeNullReturnsNullValue() {
        assertThat(adapter.normalize(null, TypeInfo.COORD).isNull()).isTrue();
    }
}
