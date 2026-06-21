package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAstNode;
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

import java.util.Objects;
import java.util.Optional;

public final class IlimapPositionResolver {

    public Optional<IlimapTokenAtPosition> tokenAt(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        if (!analysis.hasDocument()) {
            return Optional.empty();
        }

        int offset = analysis.lineMap().positionToOffset(position.line(), position.character());
        return identifierAt(analysis, offset).map(identifier -> new IlimapTokenAtPosition(
                identifier.text(), identifier.range(), smallestNodeAt(analysis, position).orElse(null)));
    }

    public Optional<IlimapAstNode> smallestNodeAt(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        if (!analysis.hasDocument()) {
            return Optional.empty();
        }
        int offset = analysis.lineMap().positionToOffset(position.line(), position.character());
        return smallestNodeAt(analysis.document(), offset);
    }

    Optional<IlimapIdeRange> identifierRangeIn(
            IlimapAnalysis analysis, IlimapSourceRange sourceRange, String identifier) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(sourceRange, "sourceRange");
        Objects.requireNonNull(identifier, "identifier");

        int startOffset = sourceRange.start().offset();
        int endOffset = sourceRange.end().offset();
        int current = startOffset;
        while (current < endOffset) {
            Optional<IdentifierSpan> span = identifierAt(analysis, current);
            if (span.isEmpty()) {
                current++;
                continue;
            }
            IdentifierSpan identifierSpan = span.get();
            if (identifierSpan.startOffset() >= startOffset
                    && identifierSpan.endOffset() <= endOffset
                    && identifierSpan.text().equals(identifier)) {
                return Optional.of(identifierSpan.range());
            }
            current = Math.max(current + 1, identifierSpan.endOffset());
        }
        return Optional.empty();
    }

    Optional<IlimapExpressionText> expressionAt(IlimapAnalysis analysis, IlimapIdeRange range) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(range, "range");

        if (!analysis.hasDocument()) {
            return Optional.empty();
        }
        int offset = analysis.lineMap().positionToOffset(range.start().line(), range.start().character());
        return expressionAt(analysis.document(), offset);
    }

    int rangeStartOffset(IlimapAnalysis analysis, IlimapIdeRange range) {
        return analysis.lineMap().positionToOffset(range.start().line(), range.start().character());
    }

    int rangeEndOffset(IlimapAnalysis analysis, IlimapIdeRange range) {
        return analysis.lineMap().positionToOffset(range.end().line(), range.end().character());
    }

    private Optional<IdentifierSpan> identifierAt(IlimapAnalysis analysis, int offset) {
        String text = analysis.text();
        if (text.isEmpty()) {
            return Optional.empty();
        }

        int clamped = Math.max(0, Math.min(offset, text.length()));
        int probe = clamped < text.length() ? clamped : text.length() - 1;
        if (!isIdentifierPart(text.charAt(probe))) {
            if (clamped > 0 && isIdentifierPart(text.charAt(clamped - 1))) {
                probe = clamped - 1;
            } else {
                return Optional.empty();
            }
        }

        int start = probe;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        int end = probe + 1;
        while (end < text.length() && isIdentifierPart(text.charAt(end))) {
            end++;
        }
        String identifier = text.substring(start, end);
        return Optional.of(new IdentifierSpan(
                identifier, start, end, new IlimapIdeRange(analysis.lineMap().toIdePosition(start), analysis.lineMap().toIdePosition(end))));
    }

    private Optional<IlimapAstNode> smallestNodeAt(IlimapDocument document, int offset) {
        if (!contains(document.range(), offset)) {
            return Optional.empty();
        }

        IlimapAstNode best = document;
        if (document.job() != null && contains(document.job().range(), offset)) {
            best = document.job();
        }
        for (var input : document.inputs()) {
            if (contains(input.range(), offset)) {
                best = input;
            }
        }
        for (var output : document.outputs()) {
            if (contains(output.range(), offset)) {
                best = output;
            }
        }
        for (var enumBlock : document.enums()) {
            if (contains(enumBlock.range(), offset)) {
                best = enumBlock;
            }
        }
        if (document.defaults() != null && contains(document.defaults().range(), offset)) {
            best = smallestAssignmentNode(document.defaults(), offset);
        }
        for (var rule : document.rules()) {
            if (contains(rule.range(), offset)) {
                return Optional.of(smallestNodeInRule(rule, offset));
            }
        }
        return Optional.of(best);
    }

    private IlimapAstNode smallestNodeInRule(IlimapRuleBlock rule, int offset) {
        IlimapAstNode best = rule;
        for (IlimapRuleElement element : rule.elements()) {
            if (contains(element.range(), offset)) {
                best = smallestNodeInElement(element, offset);
            }
        }
        return best;
    }

    private IlimapAstNode smallestNodeInElement(IlimapRuleElement element, int offset) {
        if (element instanceof IlimapAssignmentBlock assign) {
            return smallestAssignmentNode(assign, offset);
        }
        if (element instanceof IlimapDefaultsBlock defaults) {
            return smallestAssignmentNode(defaults, offset);
        }
        if (element instanceof IlimapBagBlock bag) {
            return smallestNodeInBag(bag, offset);
        }
        if (element instanceof IlimapCreateBlock create && create.assign() != null && contains(create.assign().range(), offset)) {
            return smallestAssignmentNode(create.assign(), offset);
        }
        return element;
    }

    private IlimapAstNode smallestNodeInBag(IlimapBagBlock bag, int offset) {
        IlimapAstNode best = bag;
        if (bag.from() != null && contains(bag.from().range(), offset)) {
            best = bag.from();
        }
        if (bag.parentRef() != null && contains(bag.parentRef().range(), offset)) {
            best = bag.parentRef();
        }
        if (bag.assign() != null && contains(bag.assign().range(), offset)) {
            best = smallestAssignmentNode(bag.assign(), offset);
        }
        for (IlimapBagBlock nestedBag : bag.nestedBags()) {
            if (contains(nestedBag.range(), offset)) {
                best = smallestNodeInBag(nestedBag, offset);
            }
        }
        return best;
    }

    private IlimapAstNode smallestAssignmentNode(IlimapAssignmentBlock block, int offset) {
        for (IlimapAssignment assignment : block.assignments()) {
            if (contains(assignment.range(), offset)) {
                return assignment;
            }
        }
        return block;
    }

    private IlimapAstNode smallestAssignmentNode(IlimapDefaultsBlock block, int offset) {
        for (IlimapAssignment assignment : block.assignments()) {
            if (contains(assignment.range(), offset)) {
                return assignment;
            }
        }
        return block;
    }

    private Optional<IlimapExpressionText> expressionAt(IlimapDocument document, int offset) {
        if (document.defaults() != null) {
            Optional<IlimapExpressionText> expression = expressionAt(document.defaults(), offset);
            if (expression.isPresent()) {
                return expression;
            }
        }
        for (IlimapRuleBlock rule : document.rules()) {
            for (IlimapRuleElement element : rule.elements()) {
                Optional<IlimapExpressionText> expression = expressionAt(element, offset);
                if (expression.isPresent()) {
                    return expression;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<IlimapExpressionText> expressionAt(IlimapRuleElement element, int offset) {
        if (element instanceof IlimapAssignmentBlock assign) {
            return expressionAt(assign, offset);
        }
        if (element instanceof IlimapDefaultsBlock defaults) {
            return expressionAt(defaults, offset);
        }
        if (element instanceof IlimapSourceStmt source && source.where() != null && contains(source.where().range(), offset)) {
            return Optional.of(source.where());
        }
        if (element instanceof IlimapWhereStmt where && contains(where.expression().range(), offset)) {
            return Optional.of(where.expression());
        }
        if (element instanceof IlimapIdentityStmt identity) {
            return identity.expressions().stream().filter(expression -> contains(expression.range(), offset)).findFirst();
        }
        if (element instanceof IlimapJoinStmt join && contains(join.on().range(), offset)) {
            return Optional.of(join.on());
        }
        if (element instanceof IlimapBagBlock bag) {
            return expressionAt(bag, offset);
        }
        if (element instanceof IlimapRefBlock ref && ref.sourceRef() != null && contains(ref.sourceRef().range(), offset)) {
            return Optional.of(ref.sourceRef());
        }
        if (element instanceof IlimapCreateBlock create && create.assign() != null) {
            return expressionAt(create.assign(), offset);
        }
        if (element instanceof IlimapLossBlock loss) {
            if (loss.sourcePath() != null && contains(loss.sourcePath().range(), offset)) {
                return Optional.of(loss.sourcePath());
            }
            if (loss.when() != null && contains(loss.when().range(), offset)) {
                return Optional.of(loss.when());
            }
        }
        return Optional.empty();
    }

    private Optional<IlimapExpressionText> expressionAt(IlimapBagBlock bag, int offset) {
        if (bag.from() != null && bag.from().where() != null && contains(bag.from().where().range(), offset)) {
            return Optional.of(bag.from().where());
        }
        if (bag.assign() != null) {
            Optional<IlimapExpressionText> expression = expressionAt(bag.assign(), offset);
            if (expression.isPresent()) {
                return expression;
            }
        }
        for (IlimapBagBlock nestedBag : bag.nestedBags()) {
            Optional<IlimapExpressionText> expression = expressionAt(nestedBag, offset);
            if (expression.isPresent()) {
                return expression;
            }
        }
        return Optional.empty();
    }

    private Optional<IlimapExpressionText> expressionAt(IlimapAssignmentBlock block, int offset) {
        return block.assignments().stream()
                .map(IlimapAssignment::expression)
                .filter(expression -> contains(expression.range(), offset))
                .findFirst();
    }

    private Optional<IlimapExpressionText> expressionAt(IlimapDefaultsBlock block, int offset) {
        return block.assignments().stream()
                .map(IlimapAssignment::expression)
                .filter(expression -> contains(expression.range(), offset))
                .findFirst();
    }

    static boolean contains(IlimapSourceRange range, int offset) {
        return range.start().offset() <= offset && offset <= range.end().offset();
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private record IdentifierSpan(String text, int startOffset, int endOffset, IlimapIdeRange range) {}
}
