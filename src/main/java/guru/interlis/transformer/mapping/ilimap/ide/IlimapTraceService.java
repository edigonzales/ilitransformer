package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapTraceService {

    private static final Pattern SOURCE_MEMBER_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_-]*)\\.([A-Za-z_][A-Za-z0-9_-]*)\\b");

    private final IlimapExpressionDependencyService dependencyService;

    public IlimapTraceService() {
        this(new IlimapExpressionDependencyService());
    }

    public IlimapTraceService(IlimapExpressionDependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    public IlimapTraceSummary trace(IlimapAnalysis analysis, IlimapTraceParams params) {
        if (analysis == null || !analysis.hasDocument()) {
            return IlimapTraceSummary.unavailable(params.mode(), "Mapping could not be parsed.");
        }
        String mode = params.mode() != null ? params.mode() : "targetAttribute";
        return switch (mode) {
            case "targetAttribute" -> traceTargetAttribute(analysis, params.ruleId(), params.targetAttribute());
            case "sourceMember" ->
                traceSourceMember(analysis, params.ruleId(), params.sourceAlias(), params.sourceMember());
            case "rule" -> traceRule(analysis, params.ruleId());
            case "position" -> tracePosition(analysis, params.position());
            default -> IlimapTraceSummary.unavailable(mode, "Unknown trace mode: " + mode);
        };
    }

    public IlimapTraceSummary traceTargetAttribute(IlimapAnalysis analysis, String ruleId, String targetAttribute) {
        IlimapRuleBlock rule = findRule(analysis, ruleId).orElse(null);
        if (rule == null) {
            return IlimapTraceSummary.unavailable("targetAttribute", "Rule not found: " + ruleId);
        }
        IlimapTargetStmt target = target(rule);
        if (target == null) {
            return IlimapTraceSummary.unavailable("targetAttribute", "Rule has no target: " + ruleId);
        }
        if (targetAttribute == null) {
            return IlimapTraceSummary.unavailable("targetAttribute", "No target attribute provided.");
        }

        IlimapAssignmentWithContext assignmentContext = findAssignmentInRule(rule, targetAttribute);

        IlimapTraceTarget traceTarget = buildTraceTarget(analysis, target, targetAttribute, ruleId, assignmentContext);
        IlimapTraceExpression traceExpression = null;
        List<IlimapTraceDependency> traceDependencies = List.of();
        List<IlimapTraceStep> traceSteps = new ArrayList<>();

        if (assignmentContext != null && assignmentContext.expression != null) {
            String text = assignmentContext.expression.text();
            String kind = IlimapRuleDetailService.classifyAssignmentKind(text);
            traceExpression =
                    new IlimapTraceExpression(text, kind, toLocation(analysis, assignmentContext.expression.range()));
            traceDependencies =
                    dependencyService.dependenciesWithLocations(analysis, assignmentContext.expression, rule);
        }

        traceSteps.add(buildInputStep(analysis, rule));
        for (IlimapSourceStmt source : sources(rule)) {
            traceSteps.add(buildSourceStep(analysis, source));
        }
        if (traceExpression != null) {
            traceSteps.add(buildExpressionStep(traceExpression));
        }
        traceSteps.add(buildTargetStep(traceTarget));

        List<IlimapTraceUsage> usages = findUsagesOfSourceMembers(analysis, traceDependencies, ruleId);

        List<IlimapDiagnosticSummary> diagnostics = ruleDiagnostics(analysis, rule, targetAttribute);

        return new IlimapTraceSummary(
                true,
                "",
                "targetAttribute",
                ruleId,
                traceTarget,
                traceExpression,
                traceDependencies,
                usages,
                traceSteps,
                diagnostics);
    }

    public IlimapTraceSummary traceSourceMember(
            IlimapAnalysis analysis, String ruleId, String sourceAlias, String sourceMember) {
        if (sourceAlias == null && sourceMember == null) {
            return IlimapTraceSummary.unavailable("sourceMember", "No source alias or member provided.");
        }
        List<IlimapTraceUsage> usages = usagesOfSourceMember(analysis, sourceAlias, sourceMember, ruleId);
        return new IlimapTraceSummary(
                true, "", "sourceMember", ruleId, null, null, List.of(), usages, List.of(), List.of());
    }

    public IlimapTraceSummary traceRule(IlimapAnalysis analysis, String ruleId) {
        IlimapRuleBlock rule = findRule(analysis, ruleId).orElse(null);
        if (rule == null) {
            return IlimapTraceSummary.unavailable("rule", "Rule not found: " + ruleId);
        }
        IlimapTargetStmt target = target(rule);

        List<IlimapTraceStep> steps = new ArrayList<>();
        steps.add(buildInputStep(analysis, rule));
        for (IlimapSourceStmt source : sources(rule)) {
            steps.add(buildSourceStep(analysis, source));
        }
        for (AssignmentEntry entry : allAssignments(rule)) {
            String kind = IlimapRuleDetailService.classifyAssignmentKind(
                    entry.assignment.expression().text());
            steps.add(new IlimapTraceStep(
                    "rule:" + ruleId + ":" + entry.context + ":" + entry.assignment.targetAttribute(),
                    "expression",
                    entry.assignment.targetAttribute(),
                    entry.assignment.expression().text(),
                    kind,
                    toLocation(analysis, entry.assignment.range())));
        }
        if (target != null) {
            steps.add(new IlimapTraceStep(
                    IlimapOverviewNodeIds.rule(ruleId),
                    "target",
                    target.outputId(),
                    target.targetClass(),
                    "ok",
                    toLocation(analysis, target.range())));
        }

        return new IlimapTraceSummary(
                true,
                "",
                "rule",
                ruleId,
                null,
                null,
                List.of(),
                List.of(),
                steps,
                ruleDiagnostics(analysis, rule, null));
    }

    public IlimapTraceSummary tracePosition(IlimapAnalysis analysis, IlimapIdePosition position) {
        return IlimapTraceSummary.unavailable("position", "Position-based trace not yet implemented.");
    }

    public List<IlimapTraceUsage> usagesOfSourceMember(
            IlimapAnalysis analysis, String sourceAlias, String sourceMember, String ruleIdOrNull) {
        if (analysis == null || !analysis.hasDocument()) {
            return List.of();
        }
        List<IlimapTraceUsage> result = new ArrayList<>();
        for (IlimapRuleBlock rule : analysis.document().rules()) {
            if (ruleIdOrNull != null && !ruleIdOrNull.equals(rule.id())) {
                continue;
            }
            IlimapTargetStmt target = target(rule);
            if (target == null) {
                continue;
            }
            for (AssignmentEntry entry : allAssignments(rule)) {
                if (expressionUsesSourceMember(entry.assignment.expression().text(), sourceAlias, sourceMember)) {
                    result.add(new IlimapTraceUsage(
                            rule.id(),
                            target.outputId(),
                            target.targetClass(),
                            entry.assignment.targetAttribute(),
                            entry.context,
                            entry.assignment.expression().text(),
                            toLocation(analysis, entry.assignment.range())));
                }
            }
        }
        return result;
    }

    private Optional<IlimapRuleBlock> findRule(IlimapAnalysis analysis, String ruleId) {
        if (analysis == null || !analysis.hasDocument() || ruleId == null) {
            return Optional.empty();
        }
        return analysis.document().rules().stream()
                .filter(rule -> ruleId.equals(rule.id()))
                .findFirst();
    }

    private IlimapAssignmentWithContext findAssignmentInRule(IlimapRuleBlock rule, String targetAttribute) {
        for (AssignmentEntry entry : allAssignments(rule)) {
            if (targetAttribute.equals(entry.assignment.targetAttribute())) {
                return new IlimapAssignmentWithContext(entry.assignment, entry.assignment.expression(), entry.context);
            }
        }
        return null;
    }

    private record AssignmentEntry(IlimapAssignment assignment, String context, IlimapRuleElement owner) {}

    private List<AssignmentEntry> allAssignments(IlimapRuleBlock rule) {
        List<AssignmentEntry> result = new ArrayList<>();
        collectAssignments(rule.elements(), result, "assign");
        return result;
    }

    private void collectAssignments(
            List<IlimapRuleElement> elements, List<AssignmentEntry> result, String defaultContext) {
        for (IlimapRuleElement element : elements) {
            switch (element) {
                case IlimapAssignmentBlock block ->
                    block.assignments().forEach(a -> result.add(new AssignmentEntry(a, defaultContext, block)));
                case IlimapDefaultsBlock block ->
                    block.assignments().forEach(a -> result.add(new AssignmentEntry(a, "default", block)));
                case IlimapBagBlock bag -> {
                    if (bag.assign() != null) {
                        bag.assign().assignments().forEach(a -> result.add(new AssignmentEntry(a, "bag", bag)));
                    }
                    for (IlimapBagBlock nested : bag.nestedBags()) {
                        collectBagAssignments(nested, result);
                    }
                }
                case IlimapRefBlock ref -> {
                    if (ref.sourceRef() != null) {
                        result.add(new AssignmentEntry(
                                new IlimapAssignment(
                                        "sourceRef." + (ref.id() != null ? ref.id() : ""),
                                        ref.sourceRef(),
                                        ref.range()),
                                "ref",
                                ref));
                    }
                }
                case IlimapLossBlock loss -> {
                    if (loss.sourcePath() != null) {
                        result.add(new AssignmentEntry(
                                new IlimapAssignment("loss.sourcePath", loss.sourcePath(), loss.range()),
                                "loss",
                                loss));
                    }
                    if (loss.when() != null) {
                        result.add(new AssignmentEntry(
                                new IlimapAssignment("loss.when", loss.when(), loss.range()), "loss", loss));
                    }
                }
                default -> {}
            }
        }
    }

    private void collectBagAssignments(IlimapBagBlock bag, List<AssignmentEntry> result) {
        if (bag.assign() != null) {
            bag.assign().assignments().forEach(a -> result.add(new AssignmentEntry(a, "bag", bag)));
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            collectBagAssignments(nested, result);
        }
    }

    private record IlimapAssignmentWithContext(
            IlimapAssignment assignment, IlimapExpressionText expression, String context) {}

    private IlimapTraceTarget buildTraceTarget(
            IlimapAnalysis analysis,
            IlimapTargetStmt target,
            String targetAttribute,
            String ruleId,
            IlimapAssignmentWithContext assignmentContext) {
        String assignmentKind = null;
        IlimapOverviewLocation location = null;

        if (assignmentContext != null) {
            assignmentKind = assignmentContext.context;
            location = toLocation(analysis, assignmentContext.assignment().range());
        }

        IlimapClassInfo targetClass = analysis.modelIndex().classesForOutput(target.outputId()).stream()
                .filter(candidate -> candidate.qualifiedName().equals(target.targetClass()))
                .findFirst()
                .orElse(null);

        String type = null;
        String cardinality = null;
        boolean mandatory = false;
        if (targetClass != null) {
            Optional<IlimapAttributeInfo> attr = targetClass.attributes().stream()
                    .filter(a -> a.name().equals(targetAttribute))
                    .findFirst();
            if (attr.isPresent()) {
                type = attr.get().type();
                cardinality = attr.get().cardinality();
                mandatory = attr.get().mandatory();
            }
        }

        if (assignmentContext == null && targetClass != null) {
            boolean isMandatory = targetClass.attributes().stream()
                    .filter(a -> a.name().equals(targetAttribute))
                    .anyMatch(IlimapAttributeInfo::mandatory);
            if (isMandatory) {
                assignmentKind = "missing";
            }
        }

        return new IlimapTraceTarget(
                target.outputId(),
                target.targetClass(),
                targetAttribute,
                type,
                cardinality,
                mandatory,
                assignmentKind,
                location);
    }

    private IlimapTraceStep buildInputStep(IlimapAnalysis analysis, IlimapRuleBlock rule) {
        return new IlimapTraceStep(
                "rule:" + rule.id() + ":input", "input", "Input", "", "ok", toLocation(analysis, rule.range()));
    }

    private IlimapTraceStep buildSourceStep(IlimapAnalysis analysis, IlimapSourceStmt source) {
        return new IlimapTraceStep(
                "rule:" + "?" + ":source:" + source.alias(),
                "source",
                source.alias(),
                source.sourceClass(),
                "ok",
                toLocation(analysis, source.range()));
    }

    private IlimapTraceStep buildExpressionStep(IlimapTraceExpression expression) {
        return new IlimapTraceStep(
                null, "expression", "Expression", expression.text(), expression.kind(), expression.location());
    }

    private IlimapTraceStep buildTargetStep(IlimapTraceTarget target) {
        return new IlimapTraceStep(
                null,
                "target",
                target.targetAttribute(),
                target.targetClass(),
                target.assignmentKind(),
                target.location());
    }

    private List<IlimapTraceUsage> findUsagesOfSourceMembers(
            IlimapAnalysis analysis, List<IlimapTraceDependency> dependencies, String excludeRuleId) {
        if (dependencies.isEmpty()) {
            return List.of();
        }
        List<IlimapTraceUsage> result = new ArrayList<>();
        for (IlimapTraceDependency dep : dependencies) {
            if (!"sourceAttribute".equals(dep.kind())) {
                continue;
            }
            for (IlimapRuleBlock rule : analysis.document().rules()) {
                if (rule.id().equals(excludeRuleId)) {
                    continue;
                }
                IlimapTargetStmt target = target(rule);
                if (target == null) {
                    continue;
                }
                for (AssignmentEntry entry : allAssignments(rule)) {
                    if (expressionUsesSourceMember(entry.assignment.expression().text(), dep.alias(), dep.member())) {
                        result.add(new IlimapTraceUsage(
                                rule.id(),
                                target.outputId(),
                                target.targetClass(),
                                entry.assignment.targetAttribute(),
                                entry.context,
                                entry.assignment.expression().text(),
                                toLocation(analysis, entry.assignment.range())));
                    }
                }
            }
        }
        return result;
    }

    private boolean expressionUsesSourceMember(String expression, String sourceAlias, String sourceMember) {
        if (expression == null) {
            return false;
        }
        Matcher matcher = SOURCE_MEMBER_PATTERN.matcher(expression);
        while (matcher.find()) {
            String alias = matcher.group(1);
            String member = matcher.group(2);
            if ("enumMap".equals(alias)) {
                continue;
            }
            if ((sourceAlias == null || sourceAlias.equals(alias))
                    && (sourceMember == null || sourceMember.equals(member))) {
                return true;
            }
        }
        return false;
    }

    private static IlimapTargetStmt target(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static List<IlimapSourceStmt> sources(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapSourceStmt.class::isInstance)
                .map(IlimapSourceStmt.class::cast)
                .toList();
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

    private static List<IlimapDiagnosticSummary> ruleDiagnostics(
            IlimapAnalysis analysis, IlimapRuleBlock rule, String targetAttribute) {
        IlimapDiagnosticOwnerResolver ownerResolver = new IlimapDiagnosticOwnerResolver();
        return analysis.diagnostics().stream()
                .filter(diagnostic -> inside(analysis, diagnostic.range(), rule.range()))
                .filter(diagnostic -> targetAttribute == null
                        || diagnosticTargetAttribute(diagnostic, analysis, ownerResolver, targetAttribute))
                .map(diagnostic -> {
                    IlimapIdeRange range = diagnostic.range();
                    IlimapDiagnosticOwner owner = ownerResolver.resolve(analysis, diagnostic);
                    return new IlimapDiagnosticSummary(
                            diagnostic.code(),
                            diagnostic.severity().name().toLowerCase(),
                            diagnostic.message(),
                            range.start().line(),
                            range.start().character(),
                            "diagnostic:" + diagnostic.code() + ":"
                                    + range.start().line() + ":" + range.start().character(),
                            new IlimapOverviewLocation(
                                    range.start().line(),
                                    range.start().character(),
                                    range.end().line(),
                                    range.end().character()),
                            owner.ownerNodeId(),
                            owner.ruleId(),
                            owner.inputId(),
                            owner.outputId(),
                            owner.enumMapId(),
                            owner.targetClass(),
                            owner.targetAttribute());
                })
                .toList();
    }

    private static boolean diagnosticTargetAttribute(
            IlimapIdeDiagnostic diagnostic,
            IlimapAnalysis analysis,
            IlimapDiagnosticOwnerResolver resolver,
            String targetAttribute) {
        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnostic);
        return targetAttribute.equals(owner.targetAttribute());
    }

    private static boolean inside(
            IlimapAnalysis analysis, IlimapIdeRange diagnosticRange, IlimapSourceRange sourceRange) {
        int diagnosticStart = analysis.lineMap()
                .positionToOffset(
                        diagnosticRange.start().line(), diagnosticRange.start().character());
        return sourceRange.start().offset() <= diagnosticStart
                && diagnosticStart <= sourceRange.end().offset();
    }

    private static IlimapOverviewLocation toLocation(IlimapAnalysis analysis, IlimapSourceRange range) {
        if (range == null) {
            return null;
        }
        IlimapIdeRange ideRange = analysis.lineMap().toIdeRange(range);
        return new IlimapOverviewLocation(
                ideRange.start().line(),
                ideRange.start().character(),
                ideRange.end().line(),
                ideRange.end().character());
    }
}
