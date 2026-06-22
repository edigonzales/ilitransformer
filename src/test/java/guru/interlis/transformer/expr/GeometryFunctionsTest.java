package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.GeometryFunctions;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.support.TestGeometries;

import ch.interlis.iom_j.Iom_jObject;

import java.util.List;

import org.junit.jupiter.api.Test;

class GeometryFunctionsTest {

    @Test
    void coordEqualsExactMatch() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NumberValue.of(0.0)), null);
        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsExactMatchWithTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NumberValue.of(0.001)), null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsWithinTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.001, 1.0), NumberValue.of(0.01)), null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsOutsideTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.1, 1.0), NumberValue.of(0.01)), null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsDiagonalDistance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(0.0, 0.0), new CoordValue(3.0, 4.0), NumberValue.of(5.0)), null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsDiagonalDistanceJustOutside() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(0.0, 0.0), new CoordValue(3.0, 4.0), NumberValue.of(4.9)), null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNegativeTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NumberValue.of(-0.01)), null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNullFirstCoord() {
        Value result = GeometryFunctions.coordEquals(
                List.of(NullValue.INSTANCE, new CoordValue(1.0, 1.0), NumberValue.of(0.01)), null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNullSecondCoord() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), NullValue.INSTANCE, NumberValue.of(0.01)), null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNullTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NullValue.INSTANCE), null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsTooFewArgs() {
        Value result = GeometryFunctions.coordEquals(List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0)), null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsVeryCloseWithinMmTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(
                        new CoordValue(2600000.000, 1200000.000),
                        new CoordValue(2600000.001, 1200000.000),
                        NumberValue.of(0.002)),
                null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void pointOnSurfacePrefersExistingPointOnSurface() {
        var surface = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(0.0, 0.0),
                TestGeometries.coord(10.0, 0.0),
                TestGeometries.coord(10.0, 10.0),
                TestGeometries.coord(0.0, 10.0),
                TestGeometries.coord(0.0, 0.0)));
        GeometryObjectValue value = new GeometryObjectValue(TypeInfo.SURFACE, surface, new CoordValue(3.0, 4.0));

        Value result = GeometryFunctions.pointOnSurface(List.of(value), null);

        assertThat(result).isEqualTo(new CoordValue(3.0, 4.0));
    }

    @Test
    void pointOnSurfaceDerivesInteriorPointForSurfaceWithHole() {
        var surface = TestGeometries.surface(
                TestGeometries.boundary(
                        TestGeometries.coord(0.0, 0.0),
                        TestGeometries.coord(10.0, 0.0),
                        TestGeometries.coord(10.0, 10.0),
                        TestGeometries.coord(0.0, 10.0),
                        TestGeometries.coord(0.0, 0.0)),
                TestGeometries.boundary(
                        TestGeometries.coord(4.0, 4.0),
                        TestGeometries.coord(6.0, 4.0),
                        TestGeometries.coord(6.0, 6.0),
                        TestGeometries.coord(4.0, 6.0),
                        TestGeometries.coord(4.0, 4.0)));

        Value result =
                GeometryFunctions.pointOnSurface(List.of(new GeometryObjectValue(TypeInfo.SURFACE, surface)), null);

        assertThat(result).isInstanceOf(CoordValue.class);
        CoordValue coord = (CoordValue) result;
        assertThat(coord.x()).isBetween(0.0, 10.0);
        assertThat(coord.y()).isBetween(0.0, 10.0);
        assertThat(coord.x() >= 4.0 && coord.x() <= 6.0 && coord.y() >= 4.0 && coord.y() <= 6.0)
                .as("derived point must not be inside the hole")
                .isFalse();
    }

    @Test
    void pointOnSurfaceFallsBackForInvalidSurface() {
        var bowTie = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(0.0, 0.0),
                TestGeometries.coord(10.0, 10.0),
                TestGeometries.coord(0.0, 10.0),
                TestGeometries.coord(10.0, 0.0),
                TestGeometries.coord(0.0, 0.0)));

        Value result =
                GeometryFunctions.pointOnSurface(List.of(new GeometryObjectValue(TypeInfo.SURFACE, bowTie)), null);

        assertThat(result).isInstanceOf(CoordValue.class);
    }

    @Test
    void pointOnSurfaceReturnsNullAndWarningForEmptyGeometry() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(java.util.Map.of(), diagnostics, "test");

        Value result = GeometryFunctions.pointOnSurface(
                List.of(new GeometryObjectValue(TypeInfo.SURFACE, new Iom_jObject("MULTISURFACE", null))), ctx);

        assertThat(result).isEqualTo(NullValue.INSTANCE);
        assertThat(diagnostics.all()).anyMatch(d -> d.code().equals(DiagnosticCode.GEOM_AREA_POINT_MISSING));
    }
}
