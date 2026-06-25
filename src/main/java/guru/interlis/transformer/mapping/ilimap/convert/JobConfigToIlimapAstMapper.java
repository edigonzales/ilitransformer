package guru.interlis.transformer.mapping.ilimap.convert;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapReservedWords;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.JobConfigNormalizer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JobConfigToIlimapAstMapper {

    private static final IlimapSourceRange SYNTHETIC =
            new IlimapSourceRange(IlimapSourcePosition.start(), IlimapSourcePosition.start());

    private static final Pattern ENUM_MAP_PATTERN = Pattern.compile("enumMap\\s*\\(");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    public IlimapDocument map(JobConfig config) {
        JobConfigNormalizer.normalize(config);
        Set<String> enumNames = config.mapping.enums != null ? config.mapping.enums.keySet() : Set.of();

        return new IlimapDocument(
                IlimapFormatVersion.V2,
                config.job.name,
                mapJobBlock(config),
                mapInputs(config),
                mapOutputs(config),
                mapOid(config),
                mapBasket(config),
                mapEnums(config),
                mapTopLevelDefaults(config, enumNames),
                mapRules(config, enumNames),
                SYNTHETIC);
    }

    private IlimapJobBlock mapJobBlock(JobConfig config) {
        JobConfig.JobSection job = config.job;
        boolean hasContent = job.description != null
                || job.direction != null
                || (job.failPolicy != null && !"strict".equals(job.failPolicy))
                || (config.mapping.compileMode != null && !"strict".equals(config.mapping.compileMode))
                || (job.modeldir != null && !job.modeldir.isEmpty());
        if (!hasContent) {
            return null;
        }
        return new IlimapJobBlock(
                null,
                job.description,
                job.direction,
                job.failPolicy,
                config.mapping.compileMode,
                job.modeldir != null ? job.modeldir : List.of(),
                SYNTHETIC);
    }

    private List<IlimapInputBlock> mapInputs(JobConfig config) {
        List<IlimapInputBlock> result = new ArrayList<>();
        for (JobConfig.InputSpec spec : config.job.inputs) {
            result.add(new IlimapInputBlock(
                    spec.id,
                    spec.path,
                    spec.model,
                    spec.format,
                    JobConfigNormalizer.normalizeOptions(spec.options),
                    mapConnection(spec.connection),
                    mapQueries(spec.queries),
                    SYNTHETIC));
        }
        return result;
    }

    private IlimapConnectionBlock mapConnection(JobConfig.JdbcConnectionSpec connection) {
        if (connection == null) {
            return null;
        }
        Map<String, String> properties = connection.properties != null ? connection.properties : Map.of();
        return new IlimapConnectionBlock(
                connection.driver,
                connection.url,
                connection.user,
                connection.password,
                connection.userEnv,
                connection.passwordEnv,
                properties,
                SYNTHETIC);
    }

    private List<IlimapQueryBlock> mapQueries(List<JobConfig.JdbcQuerySpec> queries) {
        List<IlimapQueryBlock> result = new ArrayList<>();
        if (queries == null) {
            return result;
        }
        for (JobConfig.JdbcQuerySpec query : queries) {
            Map<String, String> columns = query.columns != null ? query.columns : Map.of();
            List<IlimapGeometryBlock> geometry = mapGeometry(query.geometry);
            result.add(new IlimapQueryBlock(
                    query.id,
                    query.topic,
                    query.clazz,
                    query.basketId,
                    query.oidColumn,
                    query.sql,
                    columns,
                    geometry,
                    SYNTHETIC));
        }
        return result;
    }

    private List<IlimapGeometryBlock> mapGeometry(List<JobConfig.JdbcGeometrySpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<IlimapGeometryBlock> result = new ArrayList<>();
        for (JobConfig.JdbcGeometrySpec spec : specs) {
            result.add(new IlimapGeometryBlock(
                    spec.attribute, spec.column, spec.encoding, spec.type, spec.srid, SYNTHETIC));
        }
        return result;
    }

    private List<IlimapOutputBlock> mapOutputs(JobConfig config) {
        List<IlimapOutputBlock> result = new ArrayList<>();
        for (JobConfig.OutputSpec spec : config.job.outputs) {
            result.add(new IlimapOutputBlock(
                    spec.id,
                    spec.path,
                    spec.model,
                    spec.format,
                    JobConfigNormalizer.normalizeOptions(spec.options),
                    SYNTHETIC));
        }
        return result;
    }

    private IlimapOidBlock mapOid(JobConfig config) {
        JobConfig.OidStrategySpec oid = config.mapping.oidStrategy;
        if (oid == null) {
            return null;
        }
        if ("integer".equals(oid.defaultStrategy) && oid.namespace == null) {
            return null;
        }
        return new IlimapOidBlock(oid.defaultStrategy, oid.namespace, SYNTHETIC);
    }

    private IlimapBasketStmt mapBasket(JobConfig config) {
        JobConfig.BasketStrategySpec basket = config.mapping.basketStrategy;
        if (basket == null) {
            return null;
        }
        if ("preserve".equals(basket.defaultStrategy)) {
            return null;
        }
        return new IlimapBasketStmt(basket.defaultStrategy, SYNTHETIC);
    }

    private List<IlimapEnumBlock> mapEnums(JobConfig config) {
        if (config.mapping.enums == null || config.mapping.enums.isEmpty()) {
            return List.of();
        }
        List<IlimapEnumBlock> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : config.mapping.enums.entrySet()) {
            List<IlimapEnumEntry> entries = new ArrayList<>();
            for (Map.Entry<String, String> e : entry.getValue().entrySet()) {
                entries.add(new IlimapEnumEntry(parseLiteral(e.getKey()), parseLiteral(e.getValue()), SYNTHETIC));
            }
            result.add(new IlimapEnumBlock(entry.getKey(), entries, SYNTHETIC));
        }
        return result;
    }

    private IlimapDefaultsBlock mapTopLevelDefaults(JobConfig config, Set<String> enumNames) {
        if (config.mapping.defaults == null || config.mapping.defaults.isEmpty()) {
            return null;
        }
        return new IlimapDefaultsBlock(mapAssignments(config.mapping.defaults, enumNames, Map.of()), SYNTHETIC);
    }

    private List<IlimapRuleBlock> mapRules(JobConfig config, Set<String> enumNames) {
        List<IlimapRuleBlock> result = new ArrayList<>();
        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            result.add(mapRule(rule, enumNames));
        }
        return result;
    }

    private IlimapRuleBlock mapRule(JobConfig.RuleSpec rule, Set<String> enumNames) {
        Map<String, String> aliasRenames = buildAliasRenames(rule);
        List<IlimapRuleElement> elements = new ArrayList<>();

        if (rule.target != null) {
            elements.add(new IlimapTargetStmt(rule.target.output, rule.target.clazz, SYNTHETIC));
        }

        for (JobConfig.SourceSpec source : rule.sources) {
            List<String> inputIds = JobConfigNormalizer.getInputIds(source);
            elements.add(new IlimapSourceStmt(
                    applyAlias(source.alias, aliasRenames),
                    inputIds,
                    source.clazz,
                    source.where != null ? exprRewrite(source.where, enumNames, aliasRenames) : null,
                    SYNTHETIC));
        }

        if (rule.where != null) {
            elements.add(new IlimapWhereStmt(exprRewrite(rule.where, enumNames, aliasRenames), SYNTHETIC));
        }

        if (rule.joins != null) {
            for (JobConfig.JoinSpec join : rule.joins) {
                elements.add(new IlimapJoinStmt(
                        join.type,
                        applyAlias(join.left, aliasRenames),
                        applyAlias(join.right, aliasRenames),
                        exprRewrite(join.on, enumNames, aliasRenames),
                        SYNTHETIC));
            }
        }

        if (rule.identity != null && rule.identity.sourceKey != null) {
            List<IlimapExpressionText> exprs = rule.identity.sourceKey.stream()
                    .map(k -> exprRewrite(k, enumNames, aliasRenames))
                    .toList();
            elements.add(new IlimapIdentityStmt(exprs, SYNTHETIC));
        }

        if (rule.assign != null && !rule.assign.isEmpty()) {
            elements.add(new IlimapAssignmentBlock(mapAssignments(rule.assign, enumNames, aliasRenames), SYNTHETIC));
        }

        if (rule.defaults != null && !rule.defaults.isEmpty()) {
            elements.add(new IlimapDefaultsBlock(mapAssignments(rule.defaults, enumNames, aliasRenames), SYNTHETIC));
        }

        if (rule.bags != null) {
            for (Map.Entry<String, JobConfig.BagSpec> bagEntry : rule.bags.entrySet()) {
                elements.add(mapBag(bagEntry.getKey(), bagEntry.getValue(), enumNames, aliasRenames));
            }
        }

        if (rule.refs != null) {
            for (JobConfig.RefMapping ref : rule.refs) {
                elements.add(mapRef(ref, enumNames, aliasRenames));
            }
        }

        if (rule.create != null) {
            for (JobConfig.CreateSpec create : rule.create) {
                elements.add(mapCreate(create, enumNames, aliasRenames));
            }
        }

        if (rule.losses != null) {
            for (JobConfig.LossSpec loss : rule.losses) {
                elements.add(mapLoss(loss, enumNames, aliasRenames));
            }
        }

        if (rule.metadata != null) {
            elements.add(new IlimapMetadataBlock(
                    rule.metadata.direction, rule.metadata.roundtrip, rule.metadata.lossiness, SYNTHETIC));
        }

        return new IlimapRuleBlock(rule.id, elements, SYNTHETIC);
    }

    private IlimapBagBlock mapBag(
            String name, JobConfig.BagSpec bag, Set<String> enumNames, Map<String, String> aliasRenames) {
        IlimapBagFromStmt from = null;
        if (bag.from != null) {
            from = new IlimapBagFromStmt(
                    applyAlias(bag.from.alias, aliasRenames),
                    bag.from.input,
                    bag.from.clazz,
                    bag.from.where != null ? exprRewrite(bag.from.where, enumNames, aliasRenames) : null,
                    SYNTHETIC);
        }

        IlimapParentRefStmt parentRef = null;
        if (bag.parentRef != null) {
            String kind;
            String refName;
            if (bag.parentRef.attribute != null) {
                kind = "attribute";
                refName = bag.parentRef.attribute;
            } else if (bag.parentRef.role != null) {
                kind = "role";
                refName = bag.parentRef.role;
            } else {
                kind = "attribute";
                refName = "";
            }
            parentRef = new IlimapParentRefStmt(
                    kind, refName, applyAlias(bag.parentRef.parentAlias, aliasRenames), SYNTHETIC);
        }

        IlimapAssignmentBlock assign = null;
        if (bag.assign != null && !bag.assign.isEmpty()) {
            assign = new IlimapAssignmentBlock(mapAssignments(bag.assign, enumNames, aliasRenames), SYNTHETIC);
        }

        List<IlimapBagBlock> nestedBags = new ArrayList<>();
        if (bag.nestedBags != null) {
            for (Map.Entry<String, JobConfig.BagSpec> entry : bag.nestedBags.entrySet()) {
                nestedBags.add(mapBag(entry.getKey(), entry.getValue(), enumNames, aliasRenames));
            }
        }

        return new IlimapBagBlock(
                name,
                bag.target,
                from,
                bag.structure,
                bag.mode,
                bag.maxItems,
                bag.where != null ? exprRewrite(bag.where, enumNames, aliasRenames) : null,
                parentRef,
                assign,
                nestedBags,
                SYNTHETIC);
    }

    private IlimapRefBlock mapRef(JobConfig.RefMapping ref, Set<String> enumNames, Map<String, String> aliasRenames) {
        String targetRuleId = null;
        IlimapExpressionText sourceRef = null;

        if (ref.targetObject != null) {
            targetRuleId = ref.targetObject.rule;
            if (ref.targetObject.sourceRef != null) {
                sourceRef = exprRewrite(ref.targetObject.sourceRef, enumNames, aliasRenames);
            }
        } else if (ref.targetRule != null) {
            targetRuleId = ref.targetRule;
            if (ref.sourceRef != null) {
                sourceRef = exprRewrite(ref.sourceRef, enumNames, aliasRenames);
            }
        }

        String refName = ref.target;
        if (refName == null || refName.isBlank()) {
            refName = ref.role;
        }
        if (refName == null || refName.isBlank()) {
            refName = ref.association;
        }

        return new IlimapRefBlock(refName, ref.association, ref.role, ref.required, targetRuleId, sourceRef, SYNTHETIC);
    }

    private IlimapCreateBlock mapCreate(
            JobConfig.CreateSpec create, Set<String> enumNames, Map<String, String> aliasRenames) {
        IlimapAssignmentBlock assign = null;
        if (create.assign != null && !create.assign.isEmpty()) {
            assign = new IlimapAssignmentBlock(mapAssignments(create.assign, enumNames, aliasRenames), SYNTHETIC);
        }
        return new IlimapCreateBlock(create.clazz, assign, SYNTHETIC);
    }

    private IlimapLossBlock mapLoss(JobConfig.LossSpec loss, Set<String> enumNames, Map<String, String> aliasRenames) {
        return new IlimapLossBlock(
                loss.sourcePath != null ? exprRewrite(loss.sourcePath, enumNames, aliasRenames) : null,
                loss.reasonCode,
                loss.description,
                loss.when != null ? exprRewrite(loss.when, enumNames, aliasRenames) : null,
                SYNTHETIC);
    }

    private List<IlimapAssignment> mapAssignments(
            Map<String, String> assignments, Set<String> enumNames, Map<String, String> aliasRenames) {
        List<IlimapAssignment> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            result.add(new IlimapAssignment(
                    entry.getKey(), exprRewrite(entry.getValue(), enumNames, aliasRenames), SYNTHETIC));
        }
        return result;
    }

    private static IlimapExpressionText expr(String text) {
        return new IlimapExpressionText(text, SYNTHETIC);
    }

    private IlimapExpressionText exprRewrite(String text, Set<String> enumNames, Map<String, String> aliasRenames) {
        if (text == null) {
            return expr(null);
        }
        String rewritten = rewriteExpression(denormalizeEnumMap(text, enumNames), aliasRenames);
        return expr(rewritten);
    }

    /**
     * Collects every source/bag alias declared in a rule and maps any alias that collides with a reserved {@code
     * .ilimap} keyword to a unique, non-reserved replacement. Aliases are rule-scoped, so nested bag aliases are
     * included to keep references consistent.
     */
    private static Map<String, String> buildAliasRenames(JobConfig.RuleSpec rule) {
        Set<String> aliases = new LinkedHashSet<>();
        collectAliases(rule, aliases);
        Map<String, String> renames = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>(aliases);
        for (String alias : aliases) {
            if (alias == null || alias.isBlank() || !IlimapReservedWords.isReserved(alias)) {
                continue;
            }
            String candidate = alias + "_";
            while (IlimapReservedWords.isReserved(candidate) || used.contains(candidate)) {
                candidate = candidate + "_";
            }
            renames.put(alias, candidate);
            used.add(candidate);
        }
        return renames;
    }

    private static void collectAliases(JobConfig.RuleSpec rule, Set<String> aliases) {
        if (rule.sources != null) {
            for (JobConfig.SourceSpec source : rule.sources) {
                if (source.alias != null && !source.alias.isBlank()) {
                    aliases.add(source.alias);
                }
            }
        }
        if (rule.bags != null) {
            for (JobConfig.BagSpec bag : rule.bags.values()) {
                collectBagAliases(bag, aliases);
            }
        }
    }

    private static void collectBagAliases(JobConfig.BagSpec bag, Set<String> aliases) {
        if (bag == null) {
            return;
        }
        if (bag.from != null && bag.from.alias != null && !bag.from.alias.isBlank()) {
            aliases.add(bag.from.alias);
        }
        if (bag.nestedBags != null) {
            for (JobConfig.BagSpec nested : bag.nestedBags.values()) {
                collectBagAliases(nested, aliases);
            }
        }
    }

    private static String applyAlias(String alias, Map<String, String> aliasRenames) {
        if (alias == null) {
            return null;
        }
        return aliasRenames.getOrDefault(alias, alias);
    }

    /**
     * Rewrites a raw expression text so it is round-trippable through the {@code .ilimap} lexer/parser:
     *
     * <ul>
     *   <li>single-quoted string literals are converted to double-quoted literals (the lexer only recognizes {@code
     *       "..."}), and
     *   <li>identifier tokens that match a renamed alias are replaced (attribute accesses after a {@code .} are left
     *       untouched).
     * </ul>
     *
     * String literal contents are copied verbatim and never subjected to alias renaming.
     */
    static String rewriteExpression(String text, Map<String, String> aliasRenames) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                int j = i + 1;
                out.append('"');
                while (j < n) {
                    char d = text.charAt(j);
                    out.append(d);
                    if (d == '\\' && j + 1 < n) {
                        out.append(text.charAt(j + 1));
                        j += 2;
                        continue;
                    }
                    j++;
                    if (d == '"') {
                        break;
                    }
                }
                i = j;
            } else if (c == '\'') {
                int j = i + 1;
                StringBuilder inner = new StringBuilder();
                while (j < n) {
                    char d = text.charAt(j);
                    if (d == '\\' && j + 1 < n) {
                        char e = text.charAt(j + 1);
                        if (e == '\'') {
                            inner.append('\'');
                        } else {
                            inner.append('\\').append(e);
                        }
                        j += 2;
                        continue;
                    }
                    if (d == '\'') {
                        j++;
                        break;
                    }
                    inner.append(d);
                    j++;
                }
                out.append('"');
                for (int k = 0; k < inner.length(); k++) {
                    char d = inner.charAt(k);
                    if (d == '"') {
                        out.append('\\');
                    }
                    out.append(d);
                }
                out.append('"');
                i = j;
            } else if (isIdentifierStart(c)) {
                int j = i;
                while (j < n && isIdentifierPart(text.charAt(j))) {
                    j++;
                }
                String word = text.substring(i, j);
                boolean attributeAccess = i > 0 && text.charAt(i - 1) == '.';
                if (!attributeAccess && aliasRenames.containsKey(word)) {
                    out.append(aliasRenames.get(word));
                } else {
                    out.append(word);
                }
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    static IlimapLiteral parseLiteral(String value) {
        if (value == null) {
            return new IlimapLiteral.NullLit(SYNTHETIC);
        }
        if ("true".equals(value)) {
            return new IlimapLiteral.BooleanLit(true, SYNTHETIC);
        }
        if ("false".equals(value)) {
            return new IlimapLiteral.BooleanLit(false, SYNTHETIC);
        }
        if ("null".equals(value)) {
            return new IlimapLiteral.NullLit(SYNTHETIC);
        }
        if (NUMBER_PATTERN.matcher(value).matches()) {
            return new IlimapLiteral.NumberLit(value, SYNTHETIC);
        }
        return new IlimapLiteral.StringLit(value, SYNTHETIC);
    }

    static String denormalizeEnumMap(String text, Set<String> enumNames) {
        if (text == null || enumNames.isEmpty()) {
            return text;
        }
        Matcher matcher = ENUM_MAP_PATTERN.matcher(text);
        StringBuilder result = null;
        int lastCopied = 0;

        while (matcher.find()) {
            int parenStart = matcher.end() - 1;
            SecondArgBounds bounds = findSecondArgBounds(text, parenStart);
            if (bounds == null) {
                continue;
            }
            String arg = text.substring(bounds.start, bounds.end).strip();
            if (arg.length() < 2) {
                continue;
            }
            char first = arg.charAt(0);
            char last = arg.charAt(arg.length() - 1);
            if (!((first == '"' && last == '"') || (first == '\'' && last == '\''))) {
                continue;
            }
            String inner = arg.substring(1, arg.length() - 1);
            if (!enumNames.contains(inner)) {
                continue;
            }
            if (result == null) {
                result = new StringBuilder();
            }
            result.append(text, lastCopied, bounds.start);
            result.append(inner);
            lastCopied = bounds.end;
        }

        if (result == null) {
            return text;
        }
        result.append(text, lastCopied, text.length());
        return result.toString();
    }

    private static SecondArgBounds findSecondArgBounds(String text, int openParenIndex) {
        int depth = 0;
        int commaIndex = -1;
        for (int i = openParenIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    if (commaIndex >= 0) {
                        int start = commaIndex + 1;
                        int end = i;
                        while (start < end && Character.isWhitespace(text.charAt(start))) {
                            start++;
                        }
                        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                            end--;
                        }
                        return new SecondArgBounds(start, end);
                    }
                    return null;
                }
            } else if (c == ',' && depth == 1) {
                if (commaIndex < 0) {
                    commaIndex = i;
                }
            } else if (c == '"') {
                i = skipString(text, i, '"');
            } else if (c == '\'') {
                i = skipString(text, i, '\'');
            }
        }
        return null;
    }

    private static int skipString(String text, int quoteIndex, char quoteChar) {
        for (int i = quoteIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quoteChar) {
                return i;
            }
        }
        return text.length() - 1;
    }

    private record SecondArgBounds(int start, int end) {}
}
