package guru.interlis.transformer.expr;

import java.util.List;

public record PolylineValue(List<CoordValue> points) implements Value {
    public PolylineValue {
        points = List.copyOf(points);
    }

    @Override
    public Object toNative() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(points.get(i).toNative());
        }
        return sb.toString();
    }

    @Override
    public String asText() {
        return toNative().toString();
    }
}
