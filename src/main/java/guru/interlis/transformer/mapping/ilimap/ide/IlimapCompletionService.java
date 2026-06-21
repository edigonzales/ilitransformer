package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbol;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class IlimapCompletionService {

    private static final List<String> TOP_LEVEL_KEYWORDS =
            List.of("job", "input", "output", "oid", "basket", "enum", "defaults", "rule");
    private static final List<String> RULE_KEYWORDS = List.of(
            "target", "source", "where", "join", "identity", "assign", "defaults", "bag", "ref", "create", "loss",
            "metadata");

    private final IlimapCompletionContextResolver contextResolver;

    public IlimapCompletionService() {
        this(new IlimapCompletionContextResolver());
    }

    IlimapCompletionService(IlimapCompletionContextResolver contextResolver) {
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver");
    }

    public List<IlimapCompletionItem> complete(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        IlimapCompletionContext context = contextResolver.resolve(analysis, position);
        return switch (context.kind()) {
            case TOP_LEVEL -> keywordItems(TOP_LEVEL_KEYWORDS, context.prefix());
            case RULE_BLOCK -> keywordItems(RULE_KEYWORDS, context.prefix());
            case TARGET_OUTPUT -> symbolItems(analysis, IlimapSymbolKind.OUTPUT, IlimapCompletionKind.OUTPUT, "output", context.prefix());
            case SOURCE_INPUT -> symbolItems(analysis, IlimapSymbolKind.INPUT, IlimapCompletionKind.INPUT, "input", context.prefix());
            case REF_TARGET_RULE -> symbolItems(analysis, IlimapSymbolKind.RULE, IlimapCompletionKind.RULE, "rule", context.prefix());
            case ENUM_MAP_ARGUMENT ->
                symbolItems(analysis, IlimapSymbolKind.ENUM_MAP, IlimapCompletionKind.ENUM_MAP, "enum map", context.prefix());
            case JOB_BLOCK, INPUT_BLOCK, OUTPUT_BLOCK, EXPRESSION, UNKNOWN -> List.of();
        };
    }

    private static List<IlimapCompletionItem> keywordItems(List<String> keywords, String prefix) {
        return keywords.stream()
                .filter(keyword -> keyword.startsWith(prefix))
                .map(keyword -> new IlimapCompletionItem(
                        keyword, IlimapCompletionKind.KEYWORD, "ILIMAP keyword", null, keyword))
                .toList();
    }

    private static List<IlimapCompletionItem> symbolItems(
            IlimapAnalysis analysis,
            IlimapSymbolKind symbolKind,
            IlimapCompletionKind completionKind,
            String detail,
            String prefix) {
        if (!analysis.hasDocument()) {
            return List.of();
        }
        return analysis.symbols().topLevelScope().allLocal().values().stream()
                .filter(symbol -> symbol.kind() == symbolKind)
                .map(symbol -> toCompletionItem(symbol, completionKind, detail))
                .filter(item -> item.label().startsWith(prefix))
                .sorted(Comparator.comparing(IlimapCompletionItem::label))
                .toList();
    }

    private static IlimapCompletionItem toCompletionItem(
            IlimapSymbol symbol, IlimapCompletionKind completionKind, String detail) {
        return new IlimapCompletionItem(symbol.name(), completionKind, detail, null, symbol.name());
    }
}
