package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapIdeRange(IlimapIdePosition start, IlimapIdePosition end) {

    public IlimapIdeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }
}
