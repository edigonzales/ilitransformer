package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.Objects;
import java.util.Optional;

public final class IlimapCompletionContextResolver {

    public IlimapCompletionContext resolve(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        int offset = analysis.lineMap().positionToOffset(position.line(), position.character());
        String prefix = identifierPrefixAt(analysis.text(), offset);
        if (!analysis.hasDocument()) {
            return new IlimapCompletionContext(IlimapCompletionContextKind.UNKNOWN, prefix, null, null);
        }

        IlimapRuleBlock currentRule = currentRuleAt(analysis.document(), offset).orElse(null);
        IlimapAstNode currentNode = smallestNodeAt(analysis.document(), offset).orElse(null);

        if (isEnumMapSecondArgument(analysis.document(), analysis.text(), offset)) {
            return new IlimapCompletionContext(
                    IlimapCompletionContextKind.ENUM_MAP_ARGUMENT, prefix, currentRule, currentNode);
        }
        if (currentNode instanceof IlimapTargetStmt target) {
            Optional<StringContext> classString = stringAfterKeyword(target, analysis, offset, "class");
            if (classString.isPresent()) {
                StringContext stringContext = classString.get();
                return new IlimapCompletionContext(
                        IlimapCompletionContextKind.TARGET_CLASS,
                        stringContext.prefix(),
                        currentRule,
                        currentNode,
                        target.outputId(),
                        stringContext.replacementRange());
            }
        }
        if (currentNode instanceof IlimapSourceStmt source) {
            Optional<StringContext> classString = stringAfterKeyword(source, analysis, offset, "class");
            if (classString.isPresent()) {
                StringContext stringContext = classString.get();
                return new IlimapCompletionContext(
                        IlimapCompletionContextKind.SOURCE_CLASS,
                        stringContext.prefix(),
                        currentRule,
                        currentNode,
                        source.inputIds().isEmpty() ? null : String.join(",", source.inputIds()),
                        stringContext.replacementRange());
            }
        }
        if (currentNode instanceof IlimapRefBlock ref && isBetweenRefTargetRuleAndSourceRef(ref, analysis.text(), offset)) {
            return new IlimapCompletionContext(
                    IlimapCompletionContextKind.REF_TARGET_RULE, prefix, currentRule, currentNode);
        }
        if (currentNode instanceof IlimapTargetStmt target && isBetweenKeywords(target, analysis.text(), offset, "target", "class")) {
            return new IlimapCompletionContext(
                    IlimapCompletionContextKind.TARGET_OUTPUT, prefix, currentRule, currentNode);
        }
        if (currentNode instanceof IlimapSourceStmt source && isBetweenSourceFromAndClass(source, analysis.text(), offset)) {
            return new IlimapCompletionContext(
                    IlimapCompletionContextKind.SOURCE_INPUT, prefix, currentRule, currentNode);
        }
        if (currentNode instanceof IlimapAssignment assignment && isAssignmentTargetPosition(assignment, analysis.text(), offset)) {
            return new IlimapCompletionContext(
                    IlimapCompletionContextKind.ASSIGN_TARGET_ATTRIBUTE,
                    prefix,
                    currentRule,
                    currentNode,
                    null,
                    identifierRangeAt(analysis, offset));
        }
        Optional<IlimapCompletionContext> aliasAttributeContext =
                sourceAliasAttributeContext(analysis, offset, prefix, currentRule, currentNode);
        if (aliasAttributeContext.isPresent()) {
            return aliasAttributeContext.get();
        }
        if (isExpressionPosition(analysis.document(), offset)) {
            return new IlimapCompletionContext(IlimapCompletionContextKind.EXPRESSION, prefix, currentRule, currentNode);
        }
        if (currentRule != null) {
            return new IlimapCompletionContext(IlimapCompletionContextKind.RULE_BLOCK, prefix, currentRule, currentNode);
        }
        if (contains(analysis.document().range(), offset)) {
            return new IlimapCompletionContext(IlimapCompletionContextKind.TOP_LEVEL, prefix, null, currentNode);
        }
        return new IlimapCompletionContext(IlimapCompletionContextKind.UNKNOWN, prefix, null, currentNode);
    }

    private static Optional<IlimapRuleBlock> currentRuleAt(IlimapDocument document, int offset) {
        return document.rules().stream().filter(rule -> contains(rule.range(), offset)).findFirst();
    }

    private static Optional<IlimapAstNode> smallestNodeAt(IlimapDocument document, int offset) {
        if (!contains(document.range(), offset)) {
            return Optional.empty();
        }

        IlimapAstNode best = document;
        if (document.job() != null && contains(document.job().range(), offset)) {
            best = document.job();
        }
        for (IlimapInputBlock input : document.inputs()) {
            if (contains(input.range(), offset)) {
                best = input;
            }
        }
        for (IlimapOutputBlock output : document.outputs()) {
            if (contains(output.range(), offset)) {
                best = output;
            }
        }
        for (IlimapEnumBlock enumBlock : document.enums()) {
            if (contains(enumBlock.range(), offset)) {
                best = enumBlock;
            }
        }
        if (document.defaults() != null && contains(document.defaults().range(), offset)) {
            best = smallestAssignmentNode(document.defaults(), offset);
        }
        for (IlimapRuleBlock rule : document.rules()) {
            if (contains(rule.range(), offset)) {
                return Optional.of(smallestNodeInRule(rule, offset));
            }
        }
        return Optional.of(best);
    }

    private static IlimapAstNode smallestNodeInRule(IlimapRuleBlock rule, int offset) {
        IlimapAstNode best = rule;
        for (IlimapRuleElement element : rule.elements()) {
            if (contains(element.range(), offset)) {
                best = smallestNodeInElement(element, offset);
            }
        }
        return best;
    }

    private static IlimapAstNode smallestNodeInElement(IlimapRuleElement element, int offset) {
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

    private static IlimapAstNode smallestNodeInBag(IlimapBagBlock bag, int offset) {
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

    private static IlimapAstNode smallestAssignmentNode(IlimapAssignmentBlock block, int offset) {
        for (IlimapAssignment assignment : block.assignments()) {
            if (contains(assignment.range(), offset)) {
                return assignment;
            }
        }
        return block;
    }

    private static IlimapAstNode smallestAssignmentNode(IlimapDefaultsBlock block, int offset) {
        for (IlimapAssignment assignment : block.assignments()) {
            if (contains(assignment.range(), offset)) {
                return assignment;
            }
        }
        return block;
    }

    private static boolean isBetweenSourceFromAndClass(IlimapSourceStmt source, String text, int offset) {
        int start = source.range().start().offset();
        int end = source.range().end().offset();
        String segment = text.substring(start, end);
        int fromStart = findWholeWord(segment, "from", 0);
        int classStart = findWholeWord(segment, "class", fromStart + "from".length());
        int relativeOffset = offset - start;
        return fromStart >= 0 && classStart >= 0 && relativeOffset >= fromStart + "from".length() && relativeOffset < classStart;
    }

    private static Optional<StringContext> stringAfterKeyword(
            IlimapAstNode node, IlimapAnalysis analysis, int offset, String keyword) {
        int start = node.range().start().offset();
        int end = node.range().end().offset();
        String segment = analysis.text().substring(start, end);
        int keywordStart = findWholeWord(segment, keyword, 0);
        if (keywordStart < 0) {
            return Optional.empty();
        }
        int quoteStart = segment.indexOf('"', keywordStart + keyword.length());
        if (quoteStart < 0) {
            return Optional.empty();
        }
        int quoteEnd = segment.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return Optional.empty();
        }

        int contentStart = start + quoteStart + 1;
        int contentEnd = start + quoteEnd;
        if (offset < contentStart || offset > contentEnd) {
            return Optional.empty();
        }

        String prefix = analysis.text().substring(contentStart, offset);
        return Optional.of(new StringContext(
                prefix,
                new IlimapIdeRange(
                        analysis.lineMap().toIdePosition(contentStart),
                        analysis.lineMap().toIdePosition(contentEnd))));
    }

    private static boolean isAssignmentTargetPosition(IlimapAssignment assignment, String text, int offset) {
        int start = assignment.range().start().offset();
        int end = assignment.range().end().offset();
        String segment = text.substring(start, end);
        int equals = segment.indexOf('=');
        return equals >= 0 && offset >= start && offset <= start + equals;
    }

    private static Optional<IlimapCompletionContext> sourceAliasAttributeContext(
            IlimapAnalysis analysis,
            int offset,
            String prefix,
            IlimapRuleBlock currentRule,
            IlimapAstNode currentNode) {
        Optional<IlimapExpressionText> expression = expressionAt(analysis.document(), offset);
        if (expression.isEmpty()) {
            return Optional.empty();
        }

        int expressionStart = expression.get().range().start().offset();
        int localOffset = offset - expressionStart;
        String text = expression.get().text();
        int prefixStart = localOffset;
        while (prefixStart > 0 && isIdentifierPart(text.charAt(prefixStart - 1))) {
            prefixStart--;
        }
        int dotOffset = prefixStart - 1;
        if (dotOffset < 1 || dotOffset >= text.length() || text.charAt(dotOffset) != '.') {
            return Optional.empty();
        }

        int aliasEnd = dotOffset;
        int aliasStart = aliasEnd;
        while (aliasStart > 0 && isIdentifierPart(text.charAt(aliasStart - 1))) {
            aliasStart--;
        }
        if (aliasStart == aliasEnd || (aliasStart > 0 && text.charAt(aliasStart - 1) == '.')) {
            return Optional.empty();
        }

        int attributeEnd = localOffset;
        while (attributeEnd < text.length() && isIdentifierPart(text.charAt(attributeEnd))) {
            attributeEnd++;
        }
        return Optional.of(new IlimapCompletionContext(
                IlimapCompletionContextKind.SOURCE_ALIAS_ATTRIBUTE,
                prefix,
                currentRule,
                currentNode,
                text.substring(aliasStart, aliasEnd),
                new IlimapIdeRange(
                        analysis.lineMap().toIdePosition(expressionStart + prefixStart),
                        analysis.lineMap().toIdePosition(expressionStart + attributeEnd))));
    }

    private static IlimapIdeRange identifierRangeAt(IlimapAnalysis analysis, int offset) {
        String text = analysis.text();
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int start = clamped;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        int end = clamped;
        while (end < text.length() && isIdentifierPart(text.charAt(end))) {
            end++;
        }
        return new IlimapIdeRange(analysis.lineMap().toIdePosition(start), analysis.lineMap().toIdePosition(end));
    }

    private static boolean isBetweenRefTargetRuleAndSourceRef(IlimapRefBlock ref, String text, int offset) {
        int start = ref.range().start().offset();
        int end = ref.range().end().offset();
        String segment = text.substring(start, end);
        int targetStart = findWholeWord(segment, "target", 0);
        int ruleStart = findWholeWord(segment, "rule", targetStart + "target".length());
        int sourceRefStart = findWholeWord(segment, "sourceRef", ruleStart + "rule".length());
        int relativeOffset = offset - start;
        return targetStart >= 0
                && ruleStart >= 0
                && sourceRefStart >= 0
                && relativeOffset >= ruleStart + "rule".length()
                && relativeOffset < sourceRefStart;
    }

    private static boolean isBetweenKeywords(
            IlimapAstNode node, String text, int offset, String firstKeyword, String secondKeyword) {
        int start = node.range().start().offset();
        int end = node.range().end().offset();
        String segment = text.substring(start, end);
        int firstStart = findWholeWord(segment, firstKeyword, 0);
        int secondStart = findWholeWord(segment, secondKeyword, firstStart + firstKeyword.length());
        int relativeOffset = offset - start;
        return firstStart >= 0
                && secondStart >= 0
                && relativeOffset >= firstStart + firstKeyword.length()
                && relativeOffset < secondStart;
    }

    private static boolean isExpressionPosition(IlimapDocument document, int offset) {
        return expressionAt(document, offset).isPresent();
    }

    private static boolean isEnumMapSecondArgument(IlimapDocument document, String text, int offset) {
        return expressionAt(document, offset)
                .map(expression -> isEnumMapSecondArgument(text, expression.range().start().offset(), offset))
                .orElse(false);
    }

    private static Optional<IlimapExpressionText> expressionAt(IlimapDocument document, int offset) {
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

    private static Optional<IlimapExpressionText> expressionAt(IlimapRuleElement element, int offset) {
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

    private static Optional<IlimapExpressionText> expressionAt(IlimapBagBlock bag, int offset) {
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

    private static Optional<IlimapExpressionText> expressionAt(IlimapAssignmentBlock block, int offset) {
        return block.assignments().stream()
                .map(IlimapAssignment::expression)
                .filter(expression -> contains(expression.range(), offset))
                .findFirst();
    }

    private static Optional<IlimapExpressionText> expressionAt(IlimapDefaultsBlock block, int offset) {
        return block.assignments().stream()
                .map(IlimapAssignment::expression)
                .filter(expression -> contains(expression.range(), offset))
                .findFirst();
    }

    private static boolean isEnumMapSecondArgument(String text, int expressionStartOffset, int cursorOffset) {
        String beforeCursor = text.substring(expressionStartOffset, cursorOffset);
        int searchFrom = 0;
        int enumMapStart = -1;
        int openParen = -1;
        while (searchFrom < beforeCursor.length()) {
            int candidate = beforeCursor.indexOf("enumMap", searchFrom);
            if (candidate < 0) {
                break;
            }
            int afterName = candidate + "enumMap".length();
            if (isWordBoundary(beforeCursor, candidate - 1) && isWordBoundary(beforeCursor, afterName)) {
                int paren = skipWhitespace(beforeCursor, afterName);
                if (paren < beforeCursor.length() && beforeCursor.charAt(paren) == '(') {
                    enumMapStart = candidate;
                    openParen = paren;
                }
            }
            searchFrom = afterName;
        }
        if (enumMapStart < 0) {
            return false;
        }

        int depth = 1;
        boolean seenTopLevelComma = false;
        for (int i = openParen + 1; i < beforeCursor.length(); i++) {
            char c = beforeCursor.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(beforeCursor, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth <= 0) {
                    return false;
                }
            } else if (c == ',' && depth == 1) {
                seenTopLevelComma = true;
            }
        }
        return seenTopLevelComma;
    }

    private static int skipQuoted(String text, int quoteOffset) {
        char quote = text.charAt(quoteOffset);
        for (int i = quoteOffset + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return text.length() - 1;
    }

    private static int skipWhitespace(String text, int offset) {
        int current = offset;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private static int findWholeWord(String text, String word, int fromIndex) {
        int start = Math.max(0, fromIndex);
        while (start < text.length()) {
            int index = text.indexOf(word, start);
            if (index < 0) {
                return -1;
            }
            if (isWordBoundary(text, index - 1) && isWordBoundary(text, index + word.length())) {
                return index;
            }
            start = index + word.length();
        }
        return -1;
    }

    private static boolean isWordBoundary(String text, int offset) {
        return offset < 0 || offset >= text.length() || !isIdentifierPart(text.charAt(offset));
    }

    private static String identifierPrefixAt(String text, int offset) {
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int start = clamped;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, clamped);
    }

    private static boolean contains(IlimapSourceRange range, int offset) {
        return range.start().offset() <= offset && offset <= range.end().offset();
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private record StringContext(String prefix, IlimapIdeRange replacementRange) {}
}
