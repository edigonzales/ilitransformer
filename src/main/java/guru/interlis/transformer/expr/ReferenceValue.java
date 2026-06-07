package guru.interlis.transformer.expr;

public record ReferenceValue(String targetClass, String oid) implements Value {
    @Override
    public String asText() {
        return oid;
    }

    @Override
    public Object toNative() {
        return oid;
    }
}
