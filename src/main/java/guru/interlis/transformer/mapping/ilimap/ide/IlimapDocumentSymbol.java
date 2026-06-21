package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;
import java.util.Objects;

public record IlimapDocumentSymbol(
        String name,
        IlimapSymbolDisplayKind kind,
        IlimapIdeRange range,
        IlimapIdeRange selectionRange,
        List<IlimapDocumentSymbol> children) {

    public IlimapDocumentSymbol {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(selectionRange, "selectionRange");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
