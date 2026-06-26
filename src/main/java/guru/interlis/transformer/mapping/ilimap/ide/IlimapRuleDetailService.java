package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagFromStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapIdentityStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapJoinStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapMetadataBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapRuleDetailService {

    private static final Pattern ENUM_MAP_PATTERN =
            Pattern.compile("enumMap\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_-]*)\\s*\\)");
    private static final Pattern SOURCE_MEMBER_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_-]*)\\.([A-Za-z_][A-Za-z0-9_-]*)\\b");

    public IlimapRuleDetailSummary detail(IlimapAnalysis analysis, String ruleId) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(ruleId, "ruleId");
        if (!analysis.hasDocument()) {
            return IlimapRuleDetailSummary.unavailable(ruleId, "Mapping could not be parsed.");
        }
        IlimapRuleBlock rule = analysis.document().rules().stream()
                .filter(candidate -> ruleId.equals(candidate.id()))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return IlimapRuleDetailSummary.unavailable(ruleId, "Rule not found: " + ruleId);
        }

        List<IlimapRuleElement> elements = rule.elements();
        IlimapTargetStmt target = find(elements, IlimapTargetStmt.class);
        IlimapMetadataBlock metadata = find(elements, IlimapMetadataBlock.class);

        return new IlimapRuleDetailSummary(
                true,
                "",
                ruleId,
                IlimapOverviewNodeIds.rule(ruleId),
                toLocation(analysis, rule.range()),
                target != null
                        ? new IlimapTargetDetailSummary(
                                target.outputId(), target.targetClass(), toLocation(analysis, target.range()))
                        : null,
                sourceDetails(analysis, elements),
                joinDetails(analysis, elements),
                identityDetails(analysis, elements),
                assignmentDetails(analysis, elements, false),
                assignmentDetails(analysis, elements, true),
                bagDetails(analysis, elements),
                refDetails(analysis, elements),
                lossDetails(analysis, elements),
                metadata != null
                        ? new IlimapMetadataSummary(
                                metadata.direction(),
                                metadata.roundtrip(),
                                metadata.lossiness(),
                                toLocation(analysis, metadata.range()))
                        : null,
                ruleDiagnostics(analysis, rule));
    }

    private static <T extends IlimapRuleElement> T find(
            List<IlimapRuleElement> elements, Class<T> type) {
        return elements.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    private static <T extends IlimapRuleElement> List<T> findAll(
            List<IlimapRuleElement> elements, Class<T> type) {
        return elements.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static List<IlimapSourceDetailSummary> sourceDetails(
            IlimapAnalysis analysis, List<IlimapRuleElement> elements) {
        return findAll(elements, IlimapSourceStmt.class).stream()
                .map(source -> new IlimapSourceDetailSummary(
                        source.alias(),
                        source.inputIds(),
                        source.sourceClass(),
                        source.where() != null ? source.where().text() : null,
                        toLocation(analysis, source.range())))
                .toList();
    }

    private static List<IlimapJoinSummary> joinDetails(
            IlimapAnalysis analysis, List<IlimapRuleElement> elements) {
        return findAll(elements, IlimapJoinStmt.class).stream()
                .map(join -> new IlimapJoinSummary(
                        join.joinType(),
                        join.leftAlias(),
                        join.rightAlias(),
                        join.on().text(),
                        toLocation(analysis, join.range())))
                .toList();
    }

    private static List<IlimapExpressionSummary> identityDetails(
            IlimapAnalysis analysis, List<IlimapRuleElement> elements) {
        IlimapIdentityStmt identity = find(elements, IlimapIdentityStmt.class);
        if (identity == null) {
            return List.of();
        }
        return identity.expressions().stream()
                .map(expr -> new IlimapExpressionSummary(
                        expr.text(), toLocation(analysis, expr.range())))
                .toList();
    }

    private static List<IlimapAssignmentSummary> assignmentDetails(
            IlimapAnalysis analysis, List<IlimapRuleElement> elements, boolean defaultsOnly) {
        List<IlimapAssignmentSummary> result = new ArrayList<>();
        Predicate<IlimapRuleElement> filter = defaultsOnly
                ? IlimapDefaultsBlock.class::isInstance
                : IlimapAssignmentBlock.class::isInstance;

        for (IlimapRuleElement element : elements) {
            if (!filter.test(element)) {
                continue;
            }
            List<IlimapAssignment> assignments;
            if (element instanceof IlimapAssignmentBlock assignmentBlock) {
                assignments = assignmentBlock.assignments();
            } else if (element instanceof IlimapDefaultsBlock defaultsBlock) {
                assignments = defaultsBlock.assignments();
            } else {
                continue;
            }
            for (IlimapAssignment assignment : assignments) {
                result.add(toAssignmentSummary(analysis, assignment, defaultsOnly ? "default" : null));
            }
        }
        return result;
    }

    private static IlimapAssignmentSummary toAssignmentSummary(
            IlimapAnalysis analysis, IlimapAssignment assignment, String forceKind) {
        String text = assignment.expression().text();
        String kind = forceKind != null ? forceKind : classifyAssignmentKind(text);
        return new IlimapAssignmentSummary(
                assignment.targetAttribute(),
                text,
                kind,
                extractDependencies(text),
                toLocation(analysis, assignment.range()));
    }

    static String classifyAssignmentKind(String expression) {
        if (expression == null || expression.isBlank()) {
            return "unknown";
        }
        String trimmed = expression.trim();
        if (trimmed.equals("null")) {
            return "null";
        }
        if (trimmed.equals("true") || trimmed.equals("false")) {
            return "constant";
        }
        if (trimmed.startsWith("\"")) {
            return "constant";
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
            return "constant";
        }
        if (trimmed.startsWith("enumMap(")) {
            return "enumMap";
        }
        if (trimmed.matches("^[A-Za-z_][A-Za-z0-9_-]*\\.[A-Za-z_][A-Za-z0-9_-]*$")) {
            return "copy";
        }
        return "computed";
    }

    static List<IlimapExpressionDependencySummary> extractDependencies(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        List<IlimapExpressionDependencySummary> result = new ArrayList<>();
        Matcher enumMapMatcher = ENUM_MAP_PATTERN.matcher(expression);
        while (enumMapMatcher.find()) {
            result.add(new IlimapExpressionDependencySummary(
                    "enumMap", null, null, null, enumMapMatcher.group(1), null, null, null));
        }
        Matcher sourceMatcher = SOURCE_MEMBER_PATTERN.matcher(expression);
        while (sourceMatcher.find()) {
            String alias = sourceMatcher.group(1);
            String member = sourceMatcher.group(2);
            if (!"enumMap".equals(alias)) {
                result.add(new IlimapExpressionDependencySummary(
                        "sourceAttribute", alias, member, null, null, null, null, null));
            }
        }
        return result;
    }

    private static List<IlimapBagSummary> bagDetails(
            IlimapAnalysis analysis, List<IlimapRuleElement> elements) {
        return findAll(elements, IlimapBagBlock.class).stream()
                .map(bag -> bagSummary(analysis, bag))
                .toList();
    }

    private static IlimapBagSummary bagSummary(IlimapAnalysis analysis, IlimapBagBlock bag) {
        return new IlimapBagSummary(
                bag.id(),
                bag.targetAttribute(),
                bag.structure(),
                bag.mode(),
                bag.maxItems(),
                bag.from() != null ? sourceDetail(analysis, bag.from()) : null,
                bag.assign() != null
                        ? bag.assign().assignments().stream()
                                .map(assignment -> toAssignmentSummary(analysis, assignment, null))
                                .toList()
                        : List.of(),
                bag.nestedBags().stream()
                        .map(nested -> bagSummary(analysis, nested))
                        .toList(),
                toLocation(analysis, bag.range()));
    }

    private static IlimapSourceDetailSummary sourceDetail(
            IlimapAnalysis analysis, IlimapBagFromStmt from) {
        return new IlimapSourceDetailSummary(
                from.alias(),
                List.of(from.inputId()),
                from.sourceClass(),
                from.where() != null ? from.where().text() : null,
                toLocation(analysis, from.range()));
    }

    private static List<IlimapRefSummary> refDetails(
            IlimapAnalysis analysis, List<IlimapRuleElement> elements) {
        return findAll(elements, IlimapRefBlock.class).stream()
                .map(ref -> new IlimapRefSummary(
                        ref.id(),
                        ref.association(),
                        ref.role(),
                        ref.required(),
                        ref.targetRuleId(),
                        ref.sourceRef() != null ? ref.sourceRef().text() : null,
                        toLocation(analysis, ref.range())))
                .toList();
    }

    private static List<IlimapLossSummary> lossDetails(
            IlimapAnalysis analysis, List<IlimapRuleElement> elements) {
        return findAll(elements, IlimapLossBlock.class).stream()
                .map(loss -> new IlimapLossSummary(
                        loss.sourcePath() != null ? loss.sourcePath().text() : null,
                        loss.reasonCode(),
                        loss.description(),
                        loss.when() != null ? loss.when().text() : null,
                        toLocation(analysis, loss.range())))
                .toList();
    }

    private static List<IlimapDiagnosticSummary> ruleDiagnostics(
            IlimapAnalysis analysis, IlimapRuleBlock rule) {
        return analysis.diagnostics().stream()
                .filter(diagnostic -> inside(analysis, diagnostic.range(), rule.range()))
                .map(diagnostic -> {
                    IlimapIdeRange range = diagnostic.range();
                    return new IlimapDiagnosticSummary(
                            diagnostic.code(),
                            diagnostic.severity().name().toLowerCase(),
                            diagnostic.message(),
                            range.start().line(),
                            range.start().character(),
                            "diagnostic:" + diagnostic.code() + ":" + range.start().line() + ":"
                                    + range.start().character(),
                            new IlimapOverviewLocation(
                                    range.start().line(),
                                    range.start().character(),
                                    range.end().line(),
                                    range.end().character()));
                })
                .toList();
    }

    private static boolean inside(
            IlimapAnalysis analysis, IlimapIdeRange diagnosticRange, IlimapSourceRange sourceRange) {
        int diagnosticStart = analysis.lineMap()
                .positionToOffset(diagnosticRange.start().line(), diagnosticRange.start().character());
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
