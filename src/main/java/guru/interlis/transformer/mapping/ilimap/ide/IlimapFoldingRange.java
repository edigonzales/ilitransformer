package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapFoldingRange(int startLine, int endLine, String kind) {

    public IlimapFoldingRange {
        Objects.requireNonNull(kind, "kind");
    }
}
