package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapCreateBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapIdentityStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapJoinStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapWhereStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapMappingSummaryService {

    private static final Pattern SOURCE_MEMBER_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_-]*)\\.([A-Za-z_][A-Za-z0-9_-]*)\\b");

    public IlimapMappingSummary summarize(IlimapAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        if (!analysis.hasDocument()) {
            return new IlimapMappingSummary(
                    true,
                    "Mapping could not be parsed.",
                    "mapping",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    diagnosticCount(analysis, IlimapIdeSeverity.ERROR),
                    diagnosticCount(analysis, IlimapIdeSeverity.WARNING),
                    diagnosticCount(analysis, IlimapIdeSeverity.INFORMATION),
                    diagnosticCount(analysis, IlimapIdeSeverity.HINT),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    diagnostics(analysis),
                    false,
                    "Model coverage unavailable because the mapping could not be parsed.",
                    List.of(),
                    List.of());
        }

        IlimapDocument document = analysis.document();
        List<IlimapRuleSummary> rules = document.rules().stream()
                .map(rule -> ruleSummary(analysis, rule))
                .toList();
        Coverage coverage = coverage(analysis, document);

        return new IlimapMappingSummary(
                true,
                "",
                mappingName(document),
                document.inputs().size(),
                document.outputs().size(),
                document.rules().size(),
                document.enums().size(),
                rules.stream().mapToInt(IlimapRuleSummary::bagCount).sum(),
                rules.stream().mapToInt(IlimapRuleSummary::refCount).sum(),
                diagnosticCount(analysis, IlimapIdeSeverity.ERROR),
                diagnosticCount(analysis, IlimapIdeSeverity.WARNING),
                diagnosticCount(analysis, IlimapIdeSeverity.INFORMATION),
                diagnosticCount(analysis, IlimapIdeSeverity.HINT),
                document.inputs().stream()
                        .map(input ->
                                new IlimapMappingInputSummary(input.id(), input.path(), input.model(), input.format()))
                        .toList(),
                document.outputs().stream()
                        .map(output -> new IlimapMappingOutputSummary(
                                output.id(), output.path(), output.model(), output.format()))
                        .toList(),
                document.enums().stream()
                        .map(enumBlock -> new IlimapEnumMapSummary(
                                enumBlock.id(), enumBlock.entries().size()))
                        .toList(),
                rules,
                diagnostics(analysis),
                coverage.available(),
                coverage.message(),
                coverage.classCoverage(),
                coverage.ruleCoverage());
    }

    private IlimapRuleSummary ruleSummary(IlimapAnalysis analysis, IlimapRuleBlock rule) {
        IlimapTargetStmt target = target(rule);
        return new IlimapRuleSummary(
                rule.id(),
                target != null ? target.outputId() : "",
                target != null ? target.targetClass() : "",
                sourceCount(rule),
                assignmentCount(rule),
                bagCount(rule),
                refCount(rule),
                status(analysis, rule));
    }

    private static IlimapTargetStmt target(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static int sourceCount(IlimapRuleBlock rule) {
        return (int) rule.elements().stream()
                .filter(IlimapSourceStmt.class::isInstance)
                .count();
    }

    private static int assignmentCount(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .mapToInt(IlimapMappingSummaryService::assignmentCount)
                .sum();
    }

    private static int assignmentCount(IlimapRuleElement element) {
        if (element instanceof IlimapAssignmentBlock assignmentBlock) {
            return assignmentBlock.assignments().size();
        }
        if (element instanceof IlimapBagBlock bag) {
            return assignmentCount(bag);
        }
        return 0;
    }

    private static int assignmentCount(IlimapBagBlock bag) {
        int count = bag.assign() != null ? bag.assign().assignments().size() : 0;
        return count
                + bag.nestedBags().stream()
                        .mapToInt(IlimapMappingSummaryService::assignmentCount)
                        .sum();
    }

    private static int bagCount(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapBagBlock.class::isInstance)
                .map(IlimapBagBlock.class::cast)
                .mapToInt(IlimapMappingSummaryService::bagCount)
                .sum();
    }

    private static int bagCount(IlimapBagBlock bag) {
        return 1
                + bag.nestedBags().stream()
                        .mapToInt(IlimapMappingSummaryService::bagCount)
                        .sum();
    }

    private static int refCount(IlimapRuleBlock rule) {
        return (int) rule.elements().stream()
                .filter(IlimapRefBlock.class::isInstance)
                .count();
    }

    private static String status(IlimapAnalysis analysis, IlimapRuleBlock rule) {
        boolean hasWarning = false;
        for (IlimapIdeDiagnostic diagnostic : analysis.diagnostics()) {
            if (inside(analysis, diagnostic.range(), rule.range())) {
                if (diagnostic.severity() == IlimapIdeSeverity.ERROR) {
                    return "error";
                }
                if (diagnostic.severity() == IlimapIdeSeverity.WARNING) {
                    hasWarning = true;
                }
            }
        }
        return hasWarning ? "warning" : "ok";
    }

    private static boolean inside(
            IlimapAnalysis analysis, IlimapIdeRange diagnosticRange, IlimapSourceRange sourceRange) {
        int diagnosticStart = analysis.lineMap()
                .positionToOffset(
                        diagnosticRange.start().line(), diagnosticRange.start().character());
        return sourceRange.start().offset() <= diagnosticStart
                && diagnosticStart <= sourceRange.end().offset();
    }

    private static int diagnosticCount(IlimapAnalysis analysis, IlimapIdeSeverity severity) {
        return (int) analysis.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == severity)
                .count();
    }

    private static List<IlimapDiagnosticSummary> diagnostics(IlimapAnalysis analysis) {
        return analysis.diagnostics().stream()
                .map(diagnostic -> new IlimapDiagnosticSummary(
                        diagnostic.code(),
                        diagnostic.severity().name().toLowerCase(),
                        diagnostic.message(),
                        diagnostic.range().start().line(),
                        diagnostic.range().start().character()))
                .toList();
    }

    private static String mappingName(IlimapDocument document) {
        if (document.name() == null || document.name().isBlank()) {
            return "mapping";
        }
        return document.name();
    }

    private Coverage coverage(IlimapAnalysis analysis, IlimapDocument document) {
        boolean hasOutputClasses = document.outputs().stream()
                .anyMatch(output ->
                        !analysis.modelIndex().classesForOutput(output.id()).isEmpty());
        if (!hasOutputClasses) {
            return new Coverage(
                    false,
                    "Model coverage unavailable until Save or Validate Mapping has loaded the models.",
                    List.of(),
                    List.of());
        }

        Map<String, List<IlimapRuleBlock>> rulesByTargetClass = new LinkedHashMap<>();
        for (IlimapRuleBlock rule : document.rules()) {
            IlimapTargetStmt target = target(rule);
            if (target != null) {
                rulesByTargetClass
                        .computeIfAbsent(target.outputId() + "\n" + target.targetClass(), ignored -> new ArrayList<>())
                        .add(rule);
            }
        }

        List<IlimapRuleCoverageSummary> ruleCoverage = document.rules().stream()
                .map(rule -> ruleCoverage(analysis, rule))
                .filter(Objects::nonNull)
                .toList();

        List<IlimapCoverageClassSummary> classCoverage = new ArrayList<>();
        for (var output : document.outputs()) {
            for (IlimapClassInfo classInfo : analysis.modelIndex().classesForOutput(output.id())) {
                String key = output.id() + "\n" + classInfo.qualifiedName();
                List<IlimapRuleBlock> targetingRules = rulesByTargetClass.getOrDefault(key, List.of());
                Set<String> assigned = new LinkedHashSet<>();
                int line = 0;
                int character = 0;
                for (IlimapRuleBlock rule : targetingRules) {
                    if (line == 0 && character == 0) {
                        IlimapIdePosition pos = analysis.lineMap()
                                .toIdePosition(rule.range().start().offset());
                        line = pos.line();
                        character = pos.character();
                    }
                    assigned.addAll(directTargetAssignments(rule).keySet());
                }
                long mandatoryMissing = classInfo.attributes().stream()
                        .filter(IlimapAttributeInfo::mandatory)
                        .filter(attribute -> !assigned.contains(attribute.name()))
                        .count();
                classCoverage.add(new IlimapCoverageClassSummary(
                        output.id(),
                        classInfo.qualifiedName(),
                        !targetingRules.isEmpty(),
                        targetingRules.stream().map(IlimapRuleBlock::id).toList(),
                        classInfo.attributes().size(),
                        assigned.size(),
                        Math.toIntExact(mandatoryMissing),
                        line,
                        character));
            }
        }
        classCoverage.sort(Comparator.comparing(IlimapCoverageClassSummary::outputId)
                .thenComparing(IlimapCoverageClassSummary::className));

        return new Coverage(true, "", classCoverage, ruleCoverage);
    }

    private IlimapRuleCoverageSummary ruleCoverage(IlimapAnalysis analysis, IlimapRuleBlock rule) {
        IlimapTargetStmt target = target(rule);
        if (target == null) {
            return null;
        }
        IlimapClassInfo targetClass = analysis.modelIndex().classesForOutput(target.outputId()).stream()
                .filter(candidate -> candidate.qualifiedName().equals(target.targetClass()))
                .findFirst()
                .orElse(null);
        if (targetClass == null) {
            return null;
        }

        Map<String, IlimapAssignment> directAssignments = directTargetAssignments(rule);
        List<IlimapCoverageAttributeSummary> attributes = targetClass.attributes().stream()
                .map(attribute -> {
                    IlimapAssignment assignment = directAssignments.get(attribute.name());
                    IlimapIdePosition pos = assignment != null
                            ? analysis.lineMap()
                                    .toIdePosition(assignment.range().start().offset())
                            : new IlimapIdePosition(-1, -1);
                    return new IlimapCoverageAttributeSummary(
                            attribute.name(),
                            attribute.type(),
                            attribute.cardinality(),
                            attribute.mandatory(),
                            assignment != null,
                            pos.line(),
                            pos.character());
                })
                .toList();

        IlimapIdePosition rulePos =
                analysis.lineMap().toIdePosition(rule.range().start().offset());
        return new IlimapRuleCoverageSummary(
                rule.id(),
                target.outputId(),
                target.targetClass(),
                attributes,
                sourceUsage(analysis, rule),
                refs(rule),
                directAssignments.size(),
                bagAssignmentCount(rule),
                rulePos.line(),
                rulePos.character());
    }

    private static Map<String, IlimapAssignment> directTargetAssignments(IlimapRuleBlock rule) {
        Map<String, IlimapAssignment> result = new LinkedHashMap<>();
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapAssignmentBlock assignments) {
                assignments
                        .assignments()
                        .forEach(assignment -> result.putIfAbsent(assignment.targetAttribute(), assignment));
            } else if (element instanceof IlimapDefaultsBlock defaults) {
                defaults.assignments()
                        .forEach(assignment -> result.putIfAbsent(assignment.targetAttribute(), assignment));
            }
        }
        return result;
    }

    private List<IlimapSourceUsageSummary> sourceUsage(IlimapAnalysis analysis, IlimapRuleBlock rule) {
        Map<String, SourceBinding> sources = new LinkedHashMap<>();
        for (IlimapRuleElement element : rule.elements()) {
            collectSources(element, sources);
        }

        Map<String, Set<String>> usedAttributes = new LinkedHashMap<>();
        Map<String, Set<String>> usedRoles = new LinkedHashMap<>();
        List<IlimapExpressionText> expressions = expressions(rule);
        for (IlimapExpressionText expression : expressions) {
            Matcher matcher = SOURCE_MEMBER_PATTERN.matcher(expression.text());
            while (matcher.find()) {
                String alias = matcher.group(1);
                String member = matcher.group(2);
                SourceBinding source = sources.get(alias);
                if (source == null) {
                    continue;
                }
                sourceClass(analysis, source).ifPresent(classInfo -> {
                    if (classInfo.findRole(member).isPresent()) {
                        usedRoles
                                .computeIfAbsent(alias, ignored -> new LinkedHashSet<>())
                                .add(member);
                    } else if (classInfo.findAttribute(member).isPresent()) {
                        usedAttributes
                                .computeIfAbsent(alias, ignored -> new LinkedHashSet<>())
                                .add(member);
                    }
                });
            }
        }

        return sources.values().stream()
                .map(source -> {
                    IlimapIdePosition pos = analysis.lineMap()
                            .toIdePosition(source.range().start().offset());
                    return new IlimapSourceUsageSummary(
                            source.alias(),
                            source.inputIds(),
                            source.sourceClass(),
                            sorted(usedAttributes.getOrDefault(source.alias(), Set.of())),
                            sorted(usedRoles.getOrDefault(source.alias(), Set.of())),
                            pos.line(),
                            pos.character());
                })
                .toList();
    }

    private Optional<IlimapClassInfo> sourceClass(IlimapAnalysis analysis, SourceBinding source) {
        for (String inputId : source.inputIds()) {
            Optional<IlimapClassInfo> classInfo = analysis.modelIndex()
                    .modelNameForInput(inputId)
                    .flatMap(modelName -> analysis.modelIndex().classesForModel(modelName).stream()
                            .filter(candidate -> candidate.qualifiedName().equals(source.sourceClass()))
                            .findFirst());
            if (classInfo.isPresent()) {
                return classInfo;
            }
        }
        return Optional.empty();
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static List<String> refs(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapRefBlock.class::isInstance)
                .map(IlimapRefBlock.class::cast)
                .map(IlimapRefBlock::id)
                .sorted()
                .toList();
    }

    private static void collectSources(IlimapRuleElement element, Map<String, SourceBinding> result) {
        if (element instanceof IlimapSourceStmt source) {
            result.putIfAbsent(
                    source.alias(),
                    new SourceBinding(source.alias(), source.inputIds(), source.sourceClass(), source.range()));
        } else if (element instanceof IlimapBagBlock bag) {
            collectSources(bag, result);
        }
    }

    private static void collectSources(IlimapBagBlock bag, Map<String, SourceBinding> result) {
        if (bag.from() != null) {
            result.putIfAbsent(
                    bag.from().alias(),
                    new SourceBinding(
                            bag.from().alias(),
                            List.of(bag.from().inputId()),
                            bag.from().sourceClass(),
                            bag.from().range()));
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            collectSources(nested, result);
        }
    }

    private static List<IlimapExpressionText> expressions(IlimapRuleBlock rule) {
        List<IlimapExpressionText> result = new ArrayList<>();
        for (IlimapRuleElement element : rule.elements()) {
            collectExpressions(element, result);
        }
        return result;
    }

    private static void collectExpressions(IlimapRuleElement element, List<IlimapExpressionText> result) {
        switch (element) {
            case IlimapAssignmentBlock block -> block.assignments().forEach(a -> result.add(a.expression()));
            case IlimapDefaultsBlock block -> block.assignments().forEach(a -> result.add(a.expression()));
            case IlimapSourceStmt source -> {
                if (source.where() != null) result.add(source.where());
            }
            case IlimapWhereStmt where -> result.add(where.expression());
            case IlimapIdentityStmt identity -> result.addAll(identity.expressions());
            case IlimapJoinStmt join -> result.add(join.on());
            case IlimapBagBlock bag -> collectExpressions(bag, result);
            case IlimapRefBlock ref -> {
                if (ref.sourceRef() != null) result.add(ref.sourceRef());
            }
            case IlimapCreateBlock create -> {
                if (create.assign() != null) {
                    create.assign().assignments().forEach(a -> result.add(a.expression()));
                }
            }
            case IlimapLossBlock loss -> {
                if (loss.sourcePath() != null) result.add(loss.sourcePath());
                if (loss.when() != null) result.add(loss.when());
            }
            default -> {}
        }
    }

    private static void collectExpressions(IlimapBagBlock bag, List<IlimapExpressionText> result) {
        if (bag.from() != null && bag.from().where() != null) {
            result.add(bag.from().where());
        }
        if (bag.assign() != null) {
            bag.assign().assignments().forEach(a -> result.add(a.expression()));
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            collectExpressions(nested, result);
        }
    }

    private static int bagAssignmentCount(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapBagBlock.class::isInstance)
                .map(IlimapBagBlock.class::cast)
                .mapToInt(IlimapMappingSummaryService::bagAssignmentCount)
                .sum();
    }

    private static int bagAssignmentCount(IlimapBagBlock bag) {
        int count = bag.assign() == null ? 0 : bag.assign().assignments().size();
        for (IlimapBagBlock nested : bag.nestedBags()) {
            count += bagAssignmentCount(nested);
        }
        return count;
    }

    private static final class Coverage {
        private final boolean available;
        private final String message;
        private final List<IlimapCoverageClassSummary> classCoverage;
        private final List<IlimapRuleCoverageSummary> ruleCoverage;

        private Coverage(
                boolean available,
                String message,
                List<IlimapCoverageClassSummary> classCoverage,
                List<IlimapRuleCoverageSummary> ruleCoverage) {
            this.available = available;
            this.message = message;
            this.classCoverage = classCoverage;
            this.ruleCoverage = ruleCoverage;
        }

        boolean available() {
            return available;
        }

        String message() {
            return message;
        }

        List<IlimapCoverageClassSummary> classCoverage() {
            return classCoverage;
        }

        List<IlimapRuleCoverageSummary> ruleCoverage() {
            return ruleCoverage;
        }
    }

    private record SourceBinding(String alias, List<String> inputIds, String sourceClass, IlimapSourceRange range) {}
}
