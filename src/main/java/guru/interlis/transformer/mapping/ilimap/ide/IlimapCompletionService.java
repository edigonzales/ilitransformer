package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class IlimapCompletionService {

    private static final List<String> TOP_LEVEL_KEYWORDS =
            List.of("job", "input", "output", "oid", "basket", "enum", "defaults", "rule");
    private static final List<String> JOB_KEYWORDS =
            List.of("description", "direction", "failPolicy", "compileMode", "modeldir");
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
            case JOB_BLOCK -> keywordItems(JOB_KEYWORDS, context.prefix());
            case RULE_BLOCK -> keywordItems(RULE_KEYWORDS, context.prefix());
            case TARGET_OUTPUT ->
                symbolItems(analysis, IlimapSymbolKind.OUTPUT, IlimapCompletionKind.OUTPUT, "output", context.prefix());
            case TARGET_CLASS -> targetClassItems(analysis, context);
            case SOURCE_INPUT ->
                symbolItems(analysis, IlimapSymbolKind.INPUT, IlimapCompletionKind.INPUT, "input", context.prefix());
            case SOURCE_CLASS -> sourceClassItems(analysis, context);
            case ASSIGN_TARGET_ATTRIBUTE -> targetAttributeItems(analysis, context);
            case SOURCE_ALIAS -> sourceAliasItems(context);
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
            case INPUT_BLOCK, OUTPUT_BLOCK, EXPRESSION, UNKNOWN -> List.of();
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
                .map(symbol -> new IlimapCompletionItem(symbol.name(), completionKind, detail, null, symbol.name()))
                .filter(item -> item.label().startsWith(prefix))
                .sorted(Comparator.comparing(IlimapCompletionItem::label))
                .toList();
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
                .map(classInfo -> memberItems(classInfo, context))
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

    private static List<IlimapCompletionItem> roleItems(List<IlimapRoleInfo> roles, IlimapCompletionContext context) {
        return roles.stream()
                .filter(role -> role.name().startsWith(context.prefix()))
                .sorted(Comparator.comparing(IlimapRoleInfo::name))
                .map(role -> new IlimapCompletionItem(
                        role.name(),
                        IlimapCompletionKind.ROLE,
                        roleDetail(role),
                        roleDocumentation(role),
                        role.name(),
                        context.replacementRange()))
                .toList();
    }

    private static List<IlimapCompletionItem> memberItems(IlimapClassInfo classInfo, IlimapCompletionContext context) {
        List<IlimapCompletionItem> result = new ArrayList<>();
        result.addAll(attributeItems(classInfo.attributes(), context));
        result.addAll(roleItems(classInfo.roles(), context));
        result.sort(Comparator.comparing(IlimapCompletionItem::label)
                .thenComparing(item -> item.kind().name()));
        return result;
    }

    private static List<IlimapCompletionItem> sourceAliasItems(IlimapCompletionContext context) {
        if (context.currentRule() == null) {
            return List.of();
        }
        return sources(context.currentRule()).stream()
                .map(SourceBinding::alias)
                .distinct()
                .filter(alias -> alias.startsWith(context.prefix()))
                .sorted()
                .map(alias -> new IlimapCompletionItem(
                        alias,
                        IlimapCompletionKind.SOURCE_ALIAS,
                        "source alias",
                        null,
                        alias,
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
        for (SourceBinding source : sources(rule)) {
            if (source.alias().equals(alias)) {
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

    private static List<SourceBinding> sources(IlimapRuleBlock rule) {
        List<SourceBinding> result = new ArrayList<>();
        for (IlimapRuleElement element : rule.elements()) {
            collectSources(element, result);
        }
        return result;
    }

    private static void collectSources(IlimapRuleElement element, List<SourceBinding> result) {
        if (element instanceof IlimapSourceStmt source) {
            result.add(new SourceBinding(source.alias(), source.inputIds(), source.sourceClass()));
        } else if (element instanceof IlimapBagBlock bag) {
            collectSources(bag, result);
        }
    }

    private static void collectSources(IlimapBagBlock bag, List<SourceBinding> result) {
        if (bag.from() != null) {
            result.add(new SourceBinding(
                    bag.from().alias(),
                    List.of(bag.from().inputId()),
                    bag.from().sourceClass()));
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            collectSources(nested, result);
        }
    }

    private static String attributeDocumentation(IlimapAttributeInfo attribute) {
        String mandatory = attribute.mandatory() ? "mandatory" : "optional";
        return mandatory + ", cardinality " + attribute.cardinality();
    }

    private static String roleDetail(IlimapRoleInfo role) {
        return role.targetClass() == null || role.targetClass().isBlank() ? "role" : "role -> " + role.targetClass();
    }

    private static String roleDocumentation(IlimapRoleInfo role) {
        List<String> parts = new ArrayList<>();
        if (role.association() != null && !role.association().isBlank()) {
            parts.add("association " + role.association());
        }
        if (!role.cardinality().isBlank()) {
            parts.add("cardinality " + role.cardinality());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private record SourceBinding(String alias, List<String> inputIds, String sourceClass) {}
}
