package guru.interlis.transformer.expr;

import guru.interlis.transformer.expr.builtins.GeometryFunctions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryFunctionsTest {

    @Test
    void coordEqualsExactMatch() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NumberValue.of(0.0)),
                null);
        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsExactMatchWithTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NumberValue.of(0.001)),
                null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsWithinTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.001, 1.0), NumberValue.of(0.01)),
                null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsOutsideTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.1, 1.0), NumberValue.of(0.01)),
                null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsDiagonalDistance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(0.0, 0.0), new CoordValue(3.0, 4.0), NumberValue.of(5.0)),
                null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void coordEqualsDiagonalDistanceJustOutside() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(0.0, 0.0), new CoordValue(3.0, 4.0), NumberValue.of(4.9)),
                null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNegativeTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NumberValue.of(-0.01)),
                null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNullFirstCoord() {
        Value result = GeometryFunctions.coordEquals(
                List.of(NullValue.INSTANCE, new CoordValue(1.0, 1.0), NumberValue.of(0.01)),
                null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNullSecondCoord() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), NullValue.INSTANCE, NumberValue.of(0.01)),
                null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsNullTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0), NullValue.INSTANCE),
                null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsTooFewArgs() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(1.0, 1.0), new CoordValue(1.0, 1.0)),
                null);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void coordEqualsVeryCloseWithinMmTolerance() {
        Value result = GeometryFunctions.coordEquals(
                List.of(new CoordValue(2600000.000, 1200000.000),
                        new CoordValue(2600000.001, 1200000.000),
                        NumberValue.of(0.002)),
                null);
        assertThat(((BooleanValue) result).value()).isTrue();
    }
}
