package guru.interlis.transformer.geometry;

import guru.interlis.transformer.mapping.plan.TypeInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryCompatibilityServiceTest {

    private final GeometryCompatibilityService service = new GeometryCompatibilityService();

    @Test
    void sameTypeIsCompatible() {
        assertCompatible(TypeInfo.COORD, TypeInfo.COORD);
        assertCompatible(TypeInfo.POLYLINE, TypeInfo.POLYLINE);
        assertCompatible(TypeInfo.SURFACE, TypeInfo.SURFACE);
        assertCompatible(TypeInfo.AREA, TypeInfo.AREA);
    }

    @Test
    void surfaceToAreaIsCompatible() {
        assertCompatible(TypeInfo.SURFACE, TypeInfo.AREA);
    }

    @Test
    void areaToSurfaceIsCompatible() {
        assertCompatible(TypeInfo.AREA, TypeInfo.SURFACE);
    }

    @Test
    void coordToPolylineIsIncompatible() {
        assertIncompatible(TypeInfo.COORD, TypeInfo.POLYLINE);
    }

    @Test
    void polylineToSurfaceIsIncompatible() {
        assertIncompatible(TypeInfo.POLYLINE, TypeInfo.SURFACE);
    }

    @Test
    void polylineToAreaIsIncompatible() {
        assertIncompatible(TypeInfo.POLYLINE, TypeInfo.AREA);
    }

    @Test
    void coordToSurfaceIsIncompatible() {
        assertIncompatible(TypeInfo.COORD, TypeInfo.SURFACE);
    }

    @Test
    void areaToCoordIsIncompatible() {
        assertIncompatible(TypeInfo.AREA, TypeInfo.COORD);
    }

    @Test
    void surfaceToCoordIsIncompatible() {
        assertIncompatible(TypeInfo.SURFACE, TypeInfo.COORD);
    }

    @Test
    void textToCoordIsIncompatible() {
        assertIncompatible(TypeInfo.TEXT, TypeInfo.COORD);
    }

    private void assertCompatible(TypeInfo source, TypeInfo target) {
        GeometryCompatibility result = service.check(source, target, null, null);
        assertThat(result.compatible())
                .as("%s -> %s should be compatible, but was: %s", source, target, result.incompatibilities())
                .isTrue();
    }

    private void assertIncompatible(TypeInfo source, TypeInfo target) {
        GeometryCompatibility result = service.check(source, target, null, null);
        assertThat(result.compatible())
                .as("%s -> %s should be incompatible", source, target)
                .isFalse();
        assertThat(result.incompatibilities()).isNotEmpty();
    }
}
