package guru.interlis.transformer.expr;

import java.util.List;

public record SurfaceValue(List<List<CoordValue>> rings) implements Value {
    public SurfaceValue {
        rings = List.copyOf(rings.stream().map(List::copyOf).toList());
    }

    @Override
    public Object toNative() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rings.size(); r++) {
            if (r > 0) sb.append("; ");
            List<CoordValue> ring = rings.get(r);
            for (int i = 0; i < ring.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(ring.get(i).toNative());
            }
        }
        return sb.toString();
    }

    @Override
    public String asText() {
        return toNative().toString();
    }
}
