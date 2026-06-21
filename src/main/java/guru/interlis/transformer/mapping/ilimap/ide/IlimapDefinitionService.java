package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbol;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.Objects;
import java.util.Optional;

public final class IlimapDefinitionService {

    private final IlimapSymbolReferenceResolver symbolResolver;

    public IlimapDefinitionService() {
        this(new IlimapSymbolReferenceResolver());
    }

    IlimapDefinitionService(IlimapSymbolReferenceResolver symbolResolver) {
        this.symbolResolver = Objects.requireNonNull(symbolResolver, "symbolResolver");
    }

    public Optional<IlimapDefinition> definitionAt(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        return symbolResolver.resolve(analysis, position).map(resolved -> toDefinition(analysis, resolved.symbol()));
    }

    private IlimapDefinition toDefinition(IlimapAnalysis analysis, IlimapSymbol symbol) {
        return new IlimapDefinition(analysis.uri(), symbolResolver.definitionRange(analysis, symbol), label(symbol));
    }

    static String label(IlimapSymbol symbol) {
        return labelPrefix(symbol.kind()) + " " + symbol.name();
    }

    private static String labelPrefix(IlimapSymbolKind kind) {
        return switch (kind) {
            case INPUT -> "input";
            case OUTPUT -> "output";
            case RULE -> "rule";
            case ENUM_MAP -> "enum";
            case SOURCE_ALIAS -> "source";
            case JOIN_ALIAS -> "join";
            case BAG -> "bag";
            case REF -> "ref";
        };
    }
}
