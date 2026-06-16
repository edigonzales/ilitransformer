package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.plan.TypeInfo;

import ch.interlis.iom_j.Iom_jObject;

import org.junit.jupiter.api.Test;

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
        NumberValue v = new NumberValue(java.math.BigDecimal.valueOf(42.5));
        assertThat(v.asNumber()).isEqualTo(42.5);
        assertThat(v.toNative()).isEqualTo(42.5);
    }

    @Test
    void numberValueIntegerAsLong() {
        NumberValue v = new NumberValue(java.math.BigDecimal.valueOf(42));
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
        assertThatThrownBy(() -> NullValue.INSTANCE.asText()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void asNumberThrowsOnNonNumber() {
        assertThatThrownBy(() -> NullValue.INSTANCE.asNumber()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void asBooleanThrowsOnNonBoolean() {
        assertThatThrownBy(() -> NullValue.INSTANCE.asBoolean()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void geometryObjectValueCopiesGeometry() {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", "1.0");
        coord.setattrvalue("C2", "2.0");

        GeometryObjectValue value = new GeometryObjectValue(TypeInfo.COORD, coord, new CoordValue(1.0, 2.0));

        coord.setattrvalue("C1", "9.0");

        Iom_jObject copy = (Iom_jObject) value.geometryObject();
        assertThat(copy.getattrvalue("C1")).isEqualTo("1.0");
        assertThat(value.pointOnSurface()).isEqualTo(new CoordValue(1.0, 2.0));
        assertThat(value.toNative().toString()).contains("COORD");
    }

    @Test
    void geometryObjectValueReturnsFreshCopies() {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", "1.0");
        coord.setattrvalue("C2", "2.0");

        GeometryObjectValue value = new GeometryObjectValue(TypeInfo.COORD, coord);
        Iom_jObject first = (Iom_jObject) value.geometryObject();
        first.setattrvalue("C1", "9.0");
        Iom_jObject second = (Iom_jObject) value.geometryObject();

        assertThat(second.getattrvalue("C1")).isEqualTo("1.0");
    }
}
