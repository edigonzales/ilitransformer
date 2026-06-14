package guru.interlis.transformer.expr;

public record BooleanValue(boolean value) implements Value {
    public static final BooleanValue TRUE = new BooleanValue(true);
    public static final BooleanValue FALSE = new BooleanValue(false);

    public static BooleanValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public boolean asBoolean() {
        return value;
    }

    @Override
    public String asText() {
        return Boolean.toString(value);
    }

    @Override
    public Object toNative() {
        return value;
    }
}
