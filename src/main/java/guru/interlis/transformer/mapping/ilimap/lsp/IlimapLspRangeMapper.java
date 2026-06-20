package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeRange;

import java.util.Objects;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class IlimapLspRangeMapper {

    public Position toLspPosition(IlimapIdePosition position) {
        Objects.requireNonNull(position, "position");
        return new Position(position.line(), position.character());
    }

    public Range toLspRange(IlimapIdeRange range) {
        Objects.requireNonNull(range, "range");
        return new Range(toLspPosition(range.start()), toLspPosition(range.end()));
    }
}
