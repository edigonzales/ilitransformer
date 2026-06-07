package guru.interlis.transformer.expr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ValueTest {

    @Test
    void nullValueIsNull() {
        assertThat(NullValue.INSTANCE.isNull()).isTrue();
        assertThat(NullValue.INSTANCE.isDefined()).isFalse();
        assertThat(NullValue.INSTANCE.toNative()).isNull();
    }

    @Test
    void textValue() {
        TextValue v = new TextValue("hello");
        assertThat(v.isNull()).isFalse();
        assertThat(v.isDefined()).isTrue();
        assertThat(v.asText()).isEqualTo("hello");
        assertThat(v.toNative()).isEqualTo("hello");
    }

    @Test
    void numberValue() {
        NumberValue v = new NumberValue(42.5);
        assertThat(v.asNumber()).isEqualTo(42.5);
        assertThat(v.toNative()).isEqualTo(42.5);
    }

    @Test
    void numberValueIntegerAsLong() {
        NumberValue v = new NumberValue(42.0);
        assertThat(v.toNative()).isEqualTo(42L);
    }

    @Test
    void booleanValue() {
        assertThat(BooleanValue.TRUE.asBoolean()).isTrue();
        assertThat(BooleanValue.TRUE.toNative()).isEqualTo(true);
        assertThat(BooleanValue.FALSE.asBoolean()).isFalse();
        assertThat(BooleanValue.of(true)).isSameAs(BooleanValue.TRUE);
        assertThat(BooleanValue.of(false)).isSameAs(BooleanValue.FALSE);
    }

    @Test
    void enumValue() {
        EnumValue v = new EnumValue("Stein", "Versicherungsart");
        assertThat(v.name()).isEqualTo("Stein");
        assertThat(v.domain()).isEqualTo("Versicherungsart");
        assertThat(v.toNative()).isEqualTo("Stein");
    }

    @Test
    void coordValue() {
        CoordValue v = new CoordValue(2600000.0, 1200000.0);
        assertThat(v.x()).isEqualTo(2600000.0);
        assertThat(v.y()).isEqualTo(1200000.0);
        assertThat(v.toNative()).isEqualTo("2600000.0 1200000.0");
    }

    @Test
    void referenceValue() {
        ReferenceValue v = new ReferenceValue("LFP3Nachfuehrung", "abc-123");
        assertThat(v.targetClass()).isEqualTo("LFP3Nachfuehrung");
        assertThat(v.oid()).isEqualTo("abc-123");
        assertThat(v.toNative()).isEqualTo("abc-123");
    }

    @Test
    void asTextThrowsOnNonText() {
        assertThatThrownBy(() -> NullValue.INSTANCE.asText())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void asNumberThrowsOnNonNumber() {
        assertThatThrownBy(() -> NullValue.INSTANCE.asNumber())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void asBooleanThrowsOnNonBoolean() {
        assertThatThrownBy(() -> NullValue.INSTANCE.asBoolean())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void polylineValue() {
        List<CoordValue> points = List.of(
                new CoordValue(2600000.0, 1200000.0),
                new CoordValue(2600100.0, 1200100.0),
                new CoordValue(2600200.0, 1200000.0));
        PolylineValue v = new PolylineValue(points);
        assertThat(v.points()).hasSize(3);
        assertThat(v.asText()).contains("2600000.0 1200000.0");
        assertThat(v.toNative().toString()).contains(", ");
    }

    @Test
    void polylineValueIsImmutable() {
        List<CoordValue> points = new java.util.ArrayList<>(List.of(
                new CoordValue(1.0, 2.0)));
        PolylineValue v = new PolylineValue(points);
        points.add(new CoordValue(3.0, 4.0));
        assertThat(v.points()).hasSize(1);
    }

    @Test
    void surfaceValue() {
        List<CoordValue> ring1 = List.of(
                new CoordValue(0.0, 0.0),
                new CoordValue(10.0, 0.0),
                new CoordValue(10.0, 10.0),
                new CoordValue(0.0, 10.0));
        SurfaceValue v = new SurfaceValue(List.of(ring1));
        assertThat(v.rings()).hasSize(1);
        assertThat(v.rings().get(0)).hasSize(4);
        assertThat(v.asText()).contains("0.0 0.0");
    }
}
