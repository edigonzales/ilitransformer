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
            List.of("name", "description", "direction", "failPolicy", "compileMode", "modeldir");
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
            case TOP_LEVEL -> topLevelItems(context);
            case JOB_BLOCK -> fieldItems(jobFields(), context);
            case INPUT_BLOCK -> fieldItems(inputOutputFields(), context);
            case OUTPUT_BLOCK -> fieldItems(inputOutputFields(), context);
            case OID_BLOCK -> fieldItems(oidFields(), context);
            case BASKET_VALUE -> valueItems(basketStrategies(), context);
            case BLOCK_FIELD_VALUE -> blockFieldValueItems(context);
            case RULE_BLOCK -> concat(keywordItems(RULE_KEYWORDS, context.prefix()), ruleSnippetItems(context));
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
            case EXPRESSION, UNKNOWN -> List.of();
        };
    }

    private static List<IlimapCompletionItem> topLevelItems(IlimapCompletionContext context) {
        List<IlimapCompletionItem> result = new ArrayList<>();
        result.addAll(keywordItems(TOP_LEVEL_KEYWORDS, context.prefix()));
        result.addAll(topLevelSnippetItems(context));
        return result;
    }

    private static List<IlimapCompletionItem> keywordItems(List<String> keywords, String prefix) {
        return keywords.stream()
                .filter(keyword -> keyword.startsWith(prefix))
                .map(keyword -> new IlimapCompletionItem(
                        keyword, IlimapCompletionKind.KEYWORD, "ILIMAP keyword", null, keyword))
                .toList();
    }

    private static List<IlimapCompletionItem> topLevelSnippetItems(IlimapCompletionContext context) {
        return List.of(
                        snippet(
                                "mapping block",
                                "ILIMAP mapping skeleton",
                                "Creates a complete mapping v2 document skeleton.",
                                "mapping v2 \"${1:name}\" {\n  job {\n    modeldir \"${2:models}\";\n  }\n\n  input ${3:src} {\n    path \"${4:input.xtf}\";\n    model \"${5:ModelName}\";\n  }\n\n  output ${6:out} {\n    path \"${7:output.xtf}\";\n    model \"${8:ModelName}\";\n  }\n\n  rule ${9:ruleId} {\n    target ${6:out} class \"${10:TargetClass}\";\n    source ${11:s} from ${3:src} class \"${12:SourceClass}\";\n    assign {\n      $0\n    }\n  }\n}",
                                context),
                        snippet(
                                "job block",
                                "ILIMAP job block",
                                "Declares mapping execution options and model directories.",
                                "job {\n  modeldir \"${1:models}\";\n  failPolicy ${2|strict,lenient,reportOnly|};\n  compileMode ${3|strict,compatible,report|};\n  $0\n}",
                                context),
                        snippet(
                                "input block",
                                "ILIMAP input block",
                                "Declares one input transfer and its INTERLIS model.",
                                "input ${1:src} {\n  path \"${2:input.xtf}\";\n  model \"${3:ModelName}\";\n  $0\n}",
                                context),
                        snippet(
                                "output block",
                                "ILIMAP output block",
                                "Declares one output transfer and its INTERLIS model.",
                                "output ${1:out} {\n  path \"${2:output.xtf}\";\n  model \"${3:ModelName}\";\n  $0\n}",
                                context),
                        snippet(
                                "oid block",
                                "ILIMAP oid block",
                                "Configures target object ID strategy.",
                                "oid {\n  strategy ${1|preserve,integer,uuid,deterministicUuid|};\n  $0\n}",
                                context),
                        snippet(
                                "enum block",
                                "ILIMAP enum map",
                                "Declares source-to-target enum value mapping.",
                                "enum ${1:EnumMap} {\n  \"${2:source}\" -> \"${3:target}\";\n  $0\n}",
                                context),
                        snippet(
                                "defaults block",
                                "ILIMAP defaults block",
                                "Declares mapping-wide default assignments.",
                                "defaults {\n  ${1:Attribute} = ${2:value};\n  $0\n}",
                                context),
                        snippet(
                                "rule block",
                                "ILIMAP rule block",
                                "Declares one target object mapping rule.",
                                "rule ${1:ruleId} {\n  target ${2:out} class \"${3:TargetClass}\";\n  source ${4:s} from ${5:src} class \"${6:SourceClass}\";\n  assign {\n    $0\n  }\n}",
                                context))
                .stream()
                .filter(item -> item.label().startsWith(context.prefix()))
                .toList();
    }

    private static List<IlimapCompletionItem> ruleSnippetItems(IlimapCompletionContext context) {
        return List.of(
                        snippet(
                                "target statement",
                                "ILIMAP target statement",
                                "Selects the output and target INTERLIS class for the rule.",
                                "target ${1:out} class \"${2:Model.Topic.Class}\";",
                                context),
                        snippet(
                                "source statement",
                                "ILIMAP source statement",
                                "Declares a source alias, input, source class, and optional filter.",
                                "source ${1:s} from ${2:src} class \"${3:Model.Topic.Class}\";",
                                context),
                        snippet(
                                "assign block",
                                "ILIMAP assign block",
                                "Assigns target attributes from source expressions.",
                                "assign {\n  ${1:TargetAttribute} = ${2:s.SourceAttribute};\n  $0\n}",
                                context),
                        snippet(
                                "ref block",
                                "ILIMAP ref block",
                                "Maps a target reference to an existing target rule via a source reference.",
                                "ref ${1:RoleName} {\n  target rule ${2:ruleId} sourceRef ${3:s.Role};\n}",
                                context),
                        snippet(
                                "bag block",
                                "ILIMAP bag block",
                                "Maps a BAG/structure target attribute from a nested source.",
                                "bag ${1:TargetBag} {\n  from ${2:s} in ${3:src} class \"${4:Model.Topic.Class}\";\n  assign {\n    ${5:TargetAttribute} = ${6:s.SourceAttribute};\n    $0\n  }\n}",
                                context))
                .stream()
                .filter(item -> item.label().startsWith(context.prefix()))
                .toList();
    }

    private static IlimapCompletionItem snippet(
            String label, String detail, String documentation, String insertText, IlimapCompletionContext context) {
        return IlimapCompletionItem.snippet(label, detail, documentation, insertText, context.replacementRange());
    }

    private static List<IlimapCompletionItem> fieldItems(List<FieldSpec> fields, IlimapCompletionContext context) {
        return fields.stream()
                .filter(field -> field.name().startsWith(context.prefix()))
                .map(field -> IlimapCompletionItem.snippet(
                        field.name(),
                        field.detail(),
                        field.documentation(),
                        field.insertText(),
                        context.replacementRange()))
                .toList();
    }

    private static List<IlimapCompletionItem> blockFieldValueItems(IlimapCompletionContext context) {
        return switch (context.qualifier()) {
            case "input.format", "output.format" ->
                valueItems(List.of("itf", "xtf", "csv", "gpkg", "jdbc", "shp"), context);
            case "job.failPolicy" -> valueItems(List.of("strict", "lenient", "reportOnly"), context);
            case "job.compileMode" -> valueItems(List.of("strict", "compatible", "report"), context);
            case "oid.strategy" -> valueItems(List.of("preserve", "integer", "uuid", "deterministicUuid"), context);
            default -> List.of();
        };
    }

    private static List<IlimapCompletionItem> valueItems(List<String> values, IlimapCompletionContext context) {
        return values.stream()
                .map(value -> new IlimapCompletionItem(
                        value,
                        IlimapCompletionKind.VALUE,
                        "ILIMAP value",
                        null,
                        value,
                        context.replacementRange(),
                        false,
                        context.prefix()))
                .toList();
    }

    private static List<String> basketStrategies() {
        return List.of("preserve", "generateUuid", "preserveOrGenerateUuid", "byTopic");
    }

    private static List<FieldSpec> jobFields() {
        return List.of(
                new FieldSpec("name", "job field", "Optional internal job name.", "name ${1:name};"),
                new FieldSpec(
                        "description",
                        "job field",
                        "Optional human-readable job description.",
                        "description \"${1:description}\";"),
                new FieldSpec(
                        "direction",
                        "job field",
                        "Optional transformation direction label.",
                        "direction ${1:direction};"),
                new FieldSpec(
                        "failPolicy",
                        "job field",
                        "Error handling policy.",
                        "failPolicy ${1|strict,lenient,reportOnly|};"),
                new FieldSpec(
                        "compileMode",
                        "job field",
                        "Mapping compiler strictness.",
                        "compileMode ${1|strict,compatible,report|};"),
                new FieldSpec(
                        "modeldir",
                        "job field",
                        "INTERLIS model repository or local model directory.",
                        "modeldir \"${1:models}\";"));
    }

    private static List<FieldSpec> inputOutputFields() {
        return List.of(
                new FieldSpec("path", "transfer field", "Required transfer file path.", "path \"${1:transfer.xtf}\";"),
                new FieldSpec("model", "transfer field", "Required INTERLIS model name.", "model \"${1:ModelName}\";"),
                new FieldSpec(
                        "format",
                        "transfer field",
                        "Optional transfer format.",
                        "format ${1|xtf,itf,csv,gpkg,jdbc,shp|};"),
                new FieldSpec(
                        "option",
                        "transfer field",
                        "Format-specific option (key-value pair).",
                        "option ${1:key} ${2:value};"),
                new FieldSpec(
                        "connection",
                        "JDBC field",
                        "JDBC connection block (only inside jdbc input).",
                        "connection {\n  ${1}\n}"),
                new FieldSpec(
                        "query",
                        "JDBC field",
                        "JDBC query block (only inside jdbc input).",
                        "query ${1:name} {\n  ${2}\n}"));
    }

    private static List<FieldSpec> oidFields() {
        return List.of(
                new FieldSpec(
                        "strategy",
                        "oid field",
                        "Target object ID strategy.",
                        "strategy ${1|preserve,integer,uuid,deterministicUuid|};"),
                new FieldSpec(
                        "namespace",
                        "oid field",
                        "Namespace used by deterministic UUID generation.",
                        "namespace \"${1:namespace}\";"));
    }

    private static List<IlimapCompletionItem> concat(
            List<IlimapCompletionItem> first, List<IlimapCompletionItem> second) {
        List<IlimapCompletionItem> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
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
        Optional<IlimapClassInfo> classInfo = sourceClassForAlias(analysis, context.currentRule(), context.qualifier());
        if (classInfo.isEmpty() && "t".equals(context.qualifier())) {
            classInfo = targetClassForRule(analysis, context.currentRule());
        }
        if (classInfo.isEmpty()) {
            return modelUnavailableHint(context);
        }
        return memberItems(classInfo.get(), context);
    }

    private static List<IlimapCompletionItem> modelUnavailableHint(IlimapCompletionContext context) {
        return List.of(new IlimapCompletionItem(
                "Validate or save to load models",
                IlimapCompletionKind.VALUE,
                "Models not loaded",
                "Run Validate Mapping or save the file to load INTERLIS models and enable attribute completions.",
                "Validate or save to load models"));
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

    private record FieldSpec(String name, String detail, String documentation, String insertText) {}

    private record SourceBinding(String alias, List<String> inputIds, String sourceClass) {}
}
