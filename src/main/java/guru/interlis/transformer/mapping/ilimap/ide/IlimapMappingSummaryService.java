package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;
import java.util.Objects;

public final class IlimapMappingSummaryService {

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
                    diagnostics(analysis));
        }

        IlimapDocument document = analysis.document();
        List<IlimapRuleSummary> rules =
                document.rules().stream().map(rule -> ruleSummary(analysis, rule)).toList();

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
                        .map(input -> new IlimapMappingInputSummary(
                                input.id(), input.path(), input.model(), input.format()))
                        .toList(),
                document.outputs().stream()
                        .map(output -> new IlimapMappingOutputSummary(
                                output.id(), output.path(), output.model(), output.format()))
                        .toList(),
                document.enums().stream()
                        .map(enumBlock -> new IlimapEnumMapSummary(enumBlock.id(), enumBlock.entries().size()))
                        .toList(),
                rules,
                diagnostics(analysis));
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
        return (int) rule.elements().stream().filter(IlimapSourceStmt.class::isInstance).count();
    }

    private static int assignmentCount(IlimapRuleBlock rule) {
        return rule.elements().stream().mapToInt(IlimapMappingSummaryService::assignmentCount).sum();
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
        return count + bag.nestedBags().stream().mapToInt(IlimapMappingSummaryService::assignmentCount).sum();
    }

    private static int bagCount(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapBagBlock.class::isInstance)
                .map(IlimapBagBlock.class::cast)
                .mapToInt(IlimapMappingSummaryService::bagCount)
                .sum();
    }

    private static int bagCount(IlimapBagBlock bag) {
        return 1 + bag.nestedBags().stream().mapToInt(IlimapMappingSummaryService::bagCount).sum();
    }

    private static int refCount(IlimapRuleBlock rule) {
        return (int) rule.elements().stream().filter(IlimapRefBlock.class::isInstance).count();
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

    private static boolean inside(IlimapAnalysis analysis, IlimapIdeRange diagnosticRange, IlimapSourceRange sourceRange) {
        int diagnosticStart = analysis.lineMap()
                .positionToOffset(diagnosticRange.start().line(), diagnosticRange.start().character());
        return sourceRange.start().offset() <= diagnosticStart && diagnosticStart <= sourceRange.end().offset();
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
}
