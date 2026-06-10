package guru.interlis.transformer.expr;

public record EnumValue(String name, String domain) implements Value {
    @Override
    public String asText() {
        return name;
    }

    @Override
    public Object toNative() {
        return name;
    }

    @Override
    public String toString() {
        return domain != null && !domain.isEmpty() ? domain + "." + name : name;
    }
}
