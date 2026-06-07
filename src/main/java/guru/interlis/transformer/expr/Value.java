package guru.interlis.transformer.expr;

public sealed interface Value
        permits TextValue, NumberValue, BooleanValue, DateValue, XmlDateTimeValue,
                EnumValue, CoordValue, PolylineValue, SurfaceValue, ReferenceValue, NullValue {

    default boolean isNull() {
        return this instanceof NullValue;
    }

    default boolean isDefined() {
        return !(this instanceof NullValue);
    }

    default String asText() {
        throw new UnsupportedOperationException("Not a text value: " + getClass().getSimpleName());
    }

    default double asNumber() {
        throw new UnsupportedOperationException("Not a numeric value: " + getClass().getSimpleName());
    }

    default boolean asBoolean() {
        throw new UnsupportedOperationException("Not a boolean value: " + getClass().getSimpleName());
    }

    Object toNative();
}
