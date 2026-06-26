package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;

import java.util.List;
import java.util.Objects;

/**
 * Resolves the nearest semantic owner (rule, input, output, enum map, target attribute) of a
 * diagnostic based on its source position and the parsed mapping AST.
 */
public final class IlimapDiagnosticOwnerResolver {

    public IlimapDiagnosticOwner resolve(IlimapAnalysis analysis, IlimapIdeDiagnostic diagnostic) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (!analysis.hasDocument()) {
            return IlimapDiagnosticOwner.none();
        }

        IlimapIdeRange range = diagnostic.range();
        int offset = analysis.lineMap()
                .positionToOffset(range.start().line(), range.start().character());
        IlimapDocument document = analysis.document();

        for (var input : document.inputs()) {
            if (IlimapPositionResolver.contains(input.range(), offset)) {
                return new IlimapDiagnosticOwner(
                        IlimapOverviewNodeIds.input(input.id()), null, input.id(), null, null, null, null);
            }
        }
        for (var output : document.outputs()) {
            if (IlimapPositionResolver.contains(output.range(), offset)) {
                return new IlimapDiagnosticOwner(
                        IlimapOverviewNodeIds.output(output.id()), null, null, output.id(), null, null, null);
            }
        }
        for (var enumBlock : document.enums()) {
            if (IlimapPositionResolver.contains(enumBlock.range(), offset)) {
                return new IlimapDiagnosticOwner(
                        IlimapOverviewNodeIds.enumMap(enumBlock.id()), null, null, null, enumBlock.id(), null, null);
            }
        }
        for (IlimapRuleBlock rule : document.rules()) {
            if (IlimapPositionResolver.contains(rule.range(), offset)) {
                return resolveInRule(rule, offset);
            }
        }
        return IlimapDiagnosticOwner.none();
    }

    private static IlimapDiagnosticOwner resolveInRule(IlimapRuleBlock rule, int offset) {
        String targetClass = targetClass(rule);
        String targetAttribute = targetAttribute(rule, offset);
        String ownerNodeId = targetAttribute != null
                ? "rule:" + rule.id() + ":assign:" + targetAttribute
                : IlimapOverviewNodeIds.rule(rule.id());
        return new IlimapDiagnosticOwner(
                ownerNodeId, rule.id(), null, null, null, targetClass, targetAttribute);
    }

    private static String targetClass(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .map(IlimapTargetStmt::targetClass)
                .findFirst()
                .orElse(null);
    }

    private static String targetAttribute(IlimapRuleBlock rule, int offset) {
        for (IlimapRuleElement element : rule.elements()) {
            String attribute = targetAttribute(element, offset);
            if (attribute != null) {
                return attribute;
            }
        }
        return null;
    }

    private static String targetAttribute(IlimapRuleElement element, int offset) {
        if (element instanceof IlimapAssignmentBlock block) {
            return targetAttribute(block.assignments(), offset);
        }
        if (element instanceof IlimapDefaultsBlock block) {
            return targetAttribute(block.assignments(), offset);
        }
        if (element instanceof IlimapBagBlock bag) {
            return targetAttributeInBag(bag, offset);
        }
        return null;
    }

    private static String targetAttributeInBag(IlimapBagBlock bag, int offset) {
        if (bag.assign() != null) {
            String attribute = targetAttribute(bag.assign().assignments(), offset);
            if (attribute != null) {
                return attribute;
            }
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            String attribute = targetAttributeInBag(nested, offset);
            if (attribute != null) {
                return attribute;
            }
        }
        return null;
    }

    private static String targetAttribute(List<IlimapAssignment> assignments, int offset) {
        for (IlimapAssignment assignment : assignments) {
            if (IlimapPositionResolver.contains(assignment.range(), offset)) {
                return assignment.targetAttribute();
            }
        }
        return null;
    }
}
