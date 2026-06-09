package guru.interlis.transformer.geometry;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.support.TestGeometries;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryDeepCopyTest {

    private final GeometryValueCopier copier = new GeometryValueCopier();

    @Test
    void deepCopyNullReturnsNull() {
        assertThat(copier.deepCopy(null)).isNull();
    }

    @Test
    void deepCopyCoordIsIndependent() {
        Iom_jObject original = new Iom_jObject("COORD", null);
        original.setattrvalue("C1", "2600000.0");
        original.setattrvalue("C2", "1200000.0");

        IomObject copy = copier.deepCopy(original);

        assertThat(copy).isNotNull();
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getattrvalue("C1")).isEqualTo("2600000.0");
        assertThat(copy.getattrvalue("C2")).isEqualTo("1200000.0");

        original.setattrvalue("C1", "9999999.0");

        assertThat(copy.getattrvalue("C1")).isEqualTo("2600000.0");
    }

    @Test
    void deepCopyPolylineIsIndependent() {
        IomObject original = TestGeometries.polyline(
                TestGeometries.coord(2600000.0, 1200000.0),
                TestGeometries.coord(2600100.0, 1200100.0));

        IomObject copy = copier.deepCopy(original);

        assertThat(copy).isNotNull();
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getattrvaluecount("sequence")).isEqualTo(1);

        original.deleteattrobj("sequence", 0);

        assertThat(copy.getattrvaluecount("sequence")).isEqualTo(1);
    }

    @Test
    void deepCopySurfaceIsIndependent() {
        IomObject original = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(0.0, 0.0),
                TestGeometries.coord(10.0, 0.0),
                TestGeometries.coord(10.0, 10.0),
                TestGeometries.coord(0.0, 0.0)));

        IomObject copy = copier.deepCopy(original);

        assertThat(copy).isNotNull();
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getattrvaluecount("surface")).isEqualTo(1);

        original.deleteattrobj("surface", 0);

        assertThat(copy.getattrvaluecount("surface")).isEqualTo(1);
    }

    @Test
    void deepCopyMultipleRoundsStillCorrect() {
        IomObject original = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(0.0, 0.0),
                TestGeometries.coord(10.0, 10.0),
                TestGeometries.coord(0.0, 0.0)));

        IomObject first = copier.deepCopy(original);
        IomObject second = copier.deepCopy(first);

        assertThat(first).isNotSameAs(original);
        assertThat(second).isNotSameAs(first);
        assertThat(second).isNotSameAs(original);

        assertThat(first.getattrvaluecount("surface")).isEqualTo(1);
        assertThat(second.getattrvaluecount("surface")).isEqualTo(1);
    }

    @Test
    void deepCopyAreaIsIndependent() {
        IomObject original = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(2601000.0, 1201000.0),
                TestGeometries.coord(2601100.0, 1201000.0),
                TestGeometries.coord(2601100.0, 1201100.0),
                TestGeometries.coord(2601000.0, 1201000.0)));

        IomObject copy = copier.deepCopy(original);

        assertThat(copy).isNotNull();
        assertThat(copy).isNotSameAs(original);

        original.addattrobj("surface", new Iom_jObject("SURFACE", null));

        int copySurfaceCount = copy.getattrvaluecount("surface");
        int originalSurfaceCount = original.getattrvaluecount("surface");
        assertThat(copySurfaceCount).isNotEqualTo(originalSurfaceCount);
    }
}
