package guru.interlis.transformer.expr;

public record CoordValue(double x, double y, Double z) implements Value {
    public CoordValue(double x, double y) {
        this(x, y, null);
    }

    @Override
    public Object toNative() {
        return x + " " + y + (z == null ? "" : " " + z);
    }
}
