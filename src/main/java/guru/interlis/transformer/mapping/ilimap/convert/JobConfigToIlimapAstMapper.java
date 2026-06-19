package guru.interlis.transformer.mapping.ilimap.convert;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;
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
            result.add(new IlimapInputBlock(spec.id, spec.path, spec.model, spec.format, SYNTHETIC));
        }
        return result;
    }

    private List<IlimapOutputBlock> mapOutputs(JobConfig config) {
        List<IlimapOutputBlock> result = new ArrayList<>();
        for (JobConfig.OutputSpec spec : config.job.outputs) {
            result.add(new IlimapOutputBlock(spec.id, spec.path, spec.model, spec.format, SYNTHETIC));
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
        return new IlimapDefaultsBlock(mapAssignments(config.mapping.defaults, enumNames), SYNTHETIC);
    }

    private List<IlimapRuleBlock> mapRules(JobConfig config, Set<String> enumNames) {
        List<IlimapRuleBlock> result = new ArrayList<>();
        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            result.add(mapRule(rule, enumNames));
        }
        return result;
    }

    private IlimapRuleBlock mapRule(JobConfig.RuleSpec rule, Set<String> enumNames) {
        List<IlimapRuleElement> elements = new ArrayList<>();

        if (rule.target != null) {
            elements.add(new IlimapTargetStmt(rule.target.output, rule.target.clazz, SYNTHETIC));
        }

        for (JobConfig.SourceSpec source : rule.sources) {
            List<String> inputIds = JobConfigNormalizer.getInputIds(source);
            elements.add(new IlimapSourceStmt(
                    source.alias,
                    inputIds,
                    source.clazz,
                    source.where != null ? expr(denormalizeEnumMap(source.where, enumNames)) : null,
                    SYNTHETIC));
        }

        if (rule.where != null) {
            elements.add(new IlimapWhereStmt(expr(denormalizeEnumMap(rule.where, enumNames)), SYNTHETIC));
        }

        if (rule.joins != null) {
            for (JobConfig.JoinSpec join : rule.joins) {
                elements.add(new IlimapJoinStmt(
                        join.type, join.left, join.right, expr(denormalizeEnumMap(join.on, enumNames)), SYNTHETIC));
            }
        }

        if (rule.identity != null && rule.identity.sourceKey != null) {
            List<IlimapExpressionText> exprs = rule.identity.sourceKey.stream()
                    .map(k -> expr(denormalizeEnumMap(k, enumNames)))
                    .toList();
            elements.add(new IlimapIdentityStmt(exprs, SYNTHETIC));
        }

        if (rule.assign != null && !rule.assign.isEmpty()) {
            elements.add(new IlimapAssignmentBlock(mapAssignments(rule.assign, enumNames), SYNTHETIC));
        }

        if (rule.defaults != null && !rule.defaults.isEmpty()) {
            elements.add(new IlimapDefaultsBlock(mapAssignments(rule.defaults, enumNames), SYNTHETIC));
        }

        if (rule.bags != null) {
            for (Map.Entry<String, JobConfig.BagSpec> bagEntry : rule.bags.entrySet()) {
                elements.add(mapBag(bagEntry.getKey(), bagEntry.getValue(), enumNames));
            }
        }

        if (rule.refs != null) {
            for (JobConfig.RefMapping ref : rule.refs) {
                elements.add(mapRef(ref, enumNames));
            }
        }

        if (rule.create != null) {
            for (JobConfig.CreateSpec create : rule.create) {
                elements.add(mapCreate(create, enumNames));
            }
        }

        if (rule.losses != null) {
            for (JobConfig.LossSpec loss : rule.losses) {
                elements.add(mapLoss(loss, enumNames));
            }
        }

        if (rule.metadata != null) {
            elements.add(new IlimapMetadataBlock(
                    rule.metadata.direction, rule.metadata.roundtrip, rule.metadata.lossiness, SYNTHETIC));
        }

        return new IlimapRuleBlock(rule.id, elements, SYNTHETIC);
    }

    private IlimapBagBlock mapBag(String name, JobConfig.BagSpec bag, Set<String> enumNames) {
        IlimapBagFromStmt from = null;
        if (bag.from != null) {
            from = new IlimapBagFromStmt(
                    bag.from.alias,
                    bag.from.input,
                    bag.from.clazz,
                    bag.from.where != null ? expr(denormalizeEnumMap(bag.from.where, enumNames)) : null,
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
            parentRef = new IlimapParentRefStmt(kind, refName, bag.parentRef.parentAlias, SYNTHETIC);
        }

        IlimapAssignmentBlock assign = null;
        if (bag.assign != null && !bag.assign.isEmpty()) {
            assign = new IlimapAssignmentBlock(mapAssignments(bag.assign, enumNames), SYNTHETIC);
        }

        List<IlimapBagBlock> nestedBags = new ArrayList<>();
        if (bag.nestedBags != null) {
            for (Map.Entry<String, JobConfig.BagSpec> entry : bag.nestedBags.entrySet()) {
                nestedBags.add(mapBag(entry.getKey(), entry.getValue(), enumNames));
            }
        }

        return new IlimapBagBlock(
                name, from, bag.structure, bag.mode, bag.maxItems, parentRef, assign, nestedBags, SYNTHETIC);
    }

    private IlimapRefBlock mapRef(JobConfig.RefMapping ref, Set<String> enumNames) {
        String targetRuleId = null;
        IlimapExpressionText sourceRef = null;

        if (ref.targetObject != null) {
            targetRuleId = ref.targetObject.rule;
            if (ref.targetObject.sourceRef != null) {
                sourceRef = expr(denormalizeEnumMap(ref.targetObject.sourceRef, enumNames));
            }
        } else if (ref.targetRule != null) {
            targetRuleId = ref.targetRule;
            if (ref.sourceRef != null) {
                sourceRef = expr(denormalizeEnumMap(ref.sourceRef, enumNames));
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

    private IlimapCreateBlock mapCreate(JobConfig.CreateSpec create, Set<String> enumNames) {
        IlimapAssignmentBlock assign = null;
        if (create.assign != null && !create.assign.isEmpty()) {
            assign = new IlimapAssignmentBlock(mapAssignments(create.assign, enumNames), SYNTHETIC);
        }
        return new IlimapCreateBlock(create.clazz, assign, SYNTHETIC);
    }

    private IlimapLossBlock mapLoss(JobConfig.LossSpec loss, Set<String> enumNames) {
        return new IlimapLossBlock(
                loss.sourcePath != null ? expr(denormalizeEnumMap(loss.sourcePath, enumNames)) : null,
                loss.reasonCode,
                loss.description,
                loss.when != null ? expr(denormalizeEnumMap(loss.when, enumNames)) : null,
                SYNTHETIC);
    }

    private List<IlimapAssignment> mapAssignments(Map<String, String> assignments, Set<String> enumNames) {
        List<IlimapAssignment> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            result.add(new IlimapAssignment(
                    entry.getKey(), expr(denormalizeEnumMap(entry.getValue(), enumNames)), SYNTHETIC));
        }
        return result;
    }

    private static IlimapExpressionText expr(String text) {
        return new IlimapExpressionText(text, SYNTHETIC);
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
