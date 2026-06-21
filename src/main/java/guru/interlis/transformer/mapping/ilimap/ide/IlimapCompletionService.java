package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbol;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class IlimapCompletionService {

    private static final List<String> TOP_LEVEL_KEYWORDS =
            List.of("job", "input", "output", "oid", "basket", "enum", "defaults", "rule");
    private static final List<String> RULE_KEYWORDS = List.of(
            "target",
            "source",
            "where",
            "join",
            "identity",
            "assign",
            "defaults",
            "bag",
            "ref",
            "create",
            "loss",
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
            case TARGET_OUTPUT ->
                symbolItems(analysis, IlimapSymbolKind.OUTPUT, IlimapCompletionKind.OUTPUT, "output", context.prefix());
            case TARGET_CLASS -> targetClassItems(analysis, context);
            case SOURCE_INPUT ->
                symbolItems(analysis, IlimapSymbolKind.INPUT, IlimapCompletionKind.INPUT, "input", context.prefix());
            case SOURCE_CLASS -> sourceClassItems(analysis, context);
            case ASSIGN_TARGET_ATTRIBUTE -> targetAttributeItems(analysis, context);
            case SOURCE_ALIAS_ATTRIBUTE -> sourceAliasAttributeItems(analysis, context);
            case REF_TARGET_RULE ->
                symbolItems(analysis, IlimapSymbolKind.RULE, IlimapCompletionKind.RULE, "rule", context.prefix());
            case ENUM_MAP_ARGUMENT ->
                symbolItems(
                        analysis,
                        IlimapSymbolKind.ENUM_MAP,
                        IlimapCompletionKind.ENUM_MAP,
                        "enum map",
                        context.prefix());
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

    private static List<IlimapCompletionItem> targetClassItems(
            IlimapAnalysis analysis, IlimapCompletionContext context) {
        if (context.qualifier() == null) {
            return List.of();
        }
        return classItems(analysis.modelIndex().classesForOutput(context.qualifier()), context);
    }

    private static List<IlimapCompletionItem> sourceClassItems(
            IlimapAnalysis analysis, IlimapCompletionContext context) {
        if (context.qualifier() == null) {
            return List.of();
        }
        Map<String, IlimapClassInfo> classesByName = new LinkedHashMap<>();
        for (String inputId : context.qualifier().split(",")) {
            for (IlimapClassInfo classInfo : analysis.modelIndex().classesForInput(inputId)) {
                classesByName.putIfAbsent(classInfo.qualifiedName(), classInfo);
            }
        }
        return classItems(List.copyOf(classesByName.values()), context);
    }

    private static List<IlimapCompletionItem> classItems(
            List<IlimapClassInfo> classes, IlimapCompletionContext context) {
        return classes.stream()
                .filter(classInfo -> classInfo.qualifiedName().startsWith(context.prefix()))
                .sorted(Comparator.comparing(IlimapClassInfo::qualifiedName))
                .map(classInfo -> new IlimapCompletionItem(
                        classInfo.qualifiedName(),
                        IlimapCompletionKind.CLASS,
                        classInfo.kind(),
                        null,
                        classInfo.qualifiedName(),
                        context.replacementRange()))
                .toList();
    }

    private static List<IlimapCompletionItem> targetAttributeItems(
            IlimapAnalysis analysis, IlimapCompletionContext context) {
        return targetClassForRule(analysis, context.currentRule())
                .map(classInfo -> attributeItems(classInfo.attributes(), context))
                .orElseGet(List::of);
    }

    private static List<IlimapCompletionItem> sourceAliasAttributeItems(
            IlimapAnalysis analysis, IlimapCompletionContext context) {
        if (context.currentRule() == null || context.qualifier() == null) {
            return List.of();
        }
        return sourceClassForAlias(analysis, context.currentRule(), context.qualifier())
                .map(classInfo -> attributeItems(classInfo.attributes(), context))
                .orElseGet(List::of);
    }

    private static List<IlimapCompletionItem> attributeItems(
            List<IlimapAttributeInfo> attributes, IlimapCompletionContext context) {
        return attributes.stream()
                .filter(attribute -> attribute.name().startsWith(context.prefix()))
                .sorted(Comparator.comparing(IlimapAttributeInfo::name))
                .map(attribute -> new IlimapCompletionItem(
                        attribute.name(),
                        IlimapCompletionKind.ATTRIBUTE,
                        attribute.type(),
                        attributeDocumentation(attribute),
                        attribute.name(),
                        context.replacementRange()))
                .toList();
    }

    private static Optional<IlimapClassInfo> targetClassForRule(IlimapAnalysis analysis, IlimapRuleBlock rule) {
        if (rule == null) {
            return Optional.empty();
        }
        return rule.elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .findFirst()
                .flatMap(target -> analysis.modelIndex()
                        .modelNameForOutput(target.outputId())
                        .flatMap(modelName -> analysis.modelIndex().classesForModel(modelName).stream()
                                .filter(classInfo -> classInfo.qualifiedName().equals(target.targetClass()))
                                .findFirst()));
    }

    private static Optional<IlimapClassInfo> sourceClassForAlias(
            IlimapAnalysis analysis, IlimapRuleBlock rule, String alias) {
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapSourceStmt source && source.alias().equals(alias)) {
                for (String inputId : source.inputIds()) {
                    Optional<IlimapClassInfo> classInfo = analysis.modelIndex()
                            .modelNameForInput(inputId)
                            .flatMap(modelName -> analysis.modelIndex().classesForModel(modelName).stream()
                                    .filter(candidate ->
                                            candidate.qualifiedName().equals(source.sourceClass()))
                                    .findFirst());
                    if (classInfo.isPresent()) {
                        return classInfo;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static String attributeDocumentation(IlimapAttributeInfo attribute) {
        String mandatory = attribute.mandatory() ? "mandatory" : "optional";
        return mandatory + ", cardinality " + attribute.cardinality();
    }
}
