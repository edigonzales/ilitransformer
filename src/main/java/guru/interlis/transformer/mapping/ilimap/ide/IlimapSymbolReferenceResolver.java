package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapInputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapOutputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbol;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.Objects;
import java.util.Optional;

final class IlimapSymbolReferenceResolver {

    private final IlimapPositionResolver positionResolver;

    IlimapSymbolReferenceResolver() {
        this(new IlimapPositionResolver());
    }

    IlimapSymbolReferenceResolver(IlimapPositionResolver positionResolver) {
        this.positionResolver = Objects.requireNonNull(positionResolver, "positionResolver");
    }

    Optional<IlimapResolvedSymbol> resolve(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        if (!analysis.hasDocument()) {
            return Optional.empty();
        }

        return positionResolver.tokenAt(analysis, position).flatMap(token -> resolve(analysis, token));
    }

    IlimapIdeRange definitionRange(IlimapAnalysis analysis, IlimapSymbol symbol) {
        return positionResolver
                .identifierRangeIn(analysis, symbol.node().range(), symbol.name())
                .orElseGet(() -> analysis.lineMap().toIdeRange(symbol.node().range()));
    }

    private Optional<IlimapResolvedSymbol> resolve(IlimapAnalysis analysis, IlimapTokenAtPosition token) {
        if (token.surroundingNode() instanceof IlimapInputBlock input
                && isDefinitionToken(analysis, token, input.id(), input.range())) {
            return analysis.symbols()
                    .resolveInput(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (token.surroundingNode() instanceof IlimapOutputBlock output
                && isDefinitionToken(analysis, token, output.id(), output.range())) {
            return analysis.symbols()
                    .resolveOutput(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (token.surroundingNode() instanceof IlimapRuleBlock rule
                && isDefinitionToken(analysis, token, rule.id(), rule.range())) {
            return analysis.symbols()
                    .resolveRule(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (token.surroundingNode() instanceof IlimapEnumBlock enumBlock
                && isDefinitionToken(analysis, token, enumBlock.id(), enumBlock.range())) {
            return analysis.symbols()
                    .resolveEnumMap(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (token.surroundingNode() instanceof IlimapTargetStmt target
                && target.outputId().equals(token.text())
                && tokenBeforeKeyword(analysis, token, target.range().start().offset(), "class")) {
            return analysis.symbols()
                    .resolveOutput(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (token.surroundingNode() instanceof IlimapSourceStmt source
                && source.inputIds().contains(token.text())
                && tokenBetweenKeywords(analysis, token, source.range().start().offset(), "from", "class")) {
            return analysis.symbols()
                    .resolveInput(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (token.surroundingNode() instanceof IlimapRefBlock ref
                && token.text().equals(ref.targetRuleId())
                && tokenBetweenKeywords(analysis, token, ref.range().start().offset(), "rule", "sourceRef")) {
            return analysis.symbols()
                    .resolveRule(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (isSourceAliasExpressionToken(analysis, token)) {
            return currentRuleAt(analysis, token)
                    .flatMap(rule -> analysis.symbols().scopeFor(rule).resolve(token.text()))
                    .filter(symbol -> symbol.kind() == IlimapSymbolKind.SOURCE_ALIAS)
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        if (isEnumMapSecondArgument(analysis, token)) {
            return analysis.symbols()
                    .resolveEnumMap(token.text())
                    .map(symbol -> new IlimapResolvedSymbol(symbol, token.range()));
        }
        return Optional.empty();
    }

    private Optional<IlimapRuleBlock> currentRuleAt(IlimapAnalysis analysis, IlimapTokenAtPosition token) {
        int tokenStart = positionResolver.rangeStartOffset(analysis, token.range());
        return analysis.document().rules().stream()
                .filter(rule -> rule.range().start().offset() <= tokenStart
                        && tokenStart <= rule.range().end().offset())
                .findFirst();
    }

    private boolean isSourceAliasExpressionToken(IlimapAnalysis analysis, IlimapTokenAtPosition token) {
        Optional<IlimapExpressionText> expression = positionResolver.expressionAt(analysis, token.range());
        if (expression.isEmpty()) {
            return false;
        }
        int expressionStart = expression.get().range().start().offset();
        int tokenStart = positionResolver.rangeStartOffset(analysis, token.range()) - expressionStart;
        int tokenEnd = positionResolver.rangeEndOffset(analysis, token.range()) - expressionStart;
        String text = expression.get().text();
        return tokenStart >= 0
                && tokenEnd < text.length()
                && text.charAt(tokenEnd) == '.'
                && (tokenStart == 0 || text.charAt(tokenStart - 1) != '.');
    }

    private boolean isDefinitionToken(
            IlimapAnalysis analysis,
            IlimapTokenAtPosition token,
            String id,
            guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange range) {
        if (!token.text().equals(id)) {
            return false;
        }
        return positionResolver
                .identifierRangeIn(analysis, range, id)
                .map(identifierRange -> sameRange(analysis, identifierRange, token.range()))
                .orElse(false);
    }

    private boolean isEnumMapSecondArgument(IlimapAnalysis analysis, IlimapTokenAtPosition token) {
        Optional<IlimapExpressionText> expression = positionResolver.expressionAt(analysis, token.range());
        if (expression.isEmpty()) {
            return false;
        }
        int expressionStart = expression.get().range().start().offset();
        int expressionEnd = expression.get().range().end().offset();
        int tokenStart = positionResolver.rangeStartOffset(analysis, token.range()) - expressionStart;
        int tokenEnd = positionResolver.rangeEndOffset(analysis, token.range()) - expressionStart;
        String expressionText = analysis.text().substring(expressionStart, expressionEnd);
        return isEnumMapSecondArgument(expressionText, token.text(), tokenStart, tokenEnd);
    }

    private boolean isEnumMapSecondArgument(String expression, String tokenText, int tokenStart, int tokenEnd) {
        int searchFrom = 0;
        while (searchFrom < expression.length()) {
            int enumMapStart = expression.indexOf("enumMap", searchFrom);
            if (enumMapStart < 0) {
                return false;
            }
            int afterName = enumMapStart + "enumMap".length();
            if (!isWordBoundary(expression, enumMapStart - 1) || !isWordBoundary(expression, afterName)) {
                searchFrom = afterName;
                continue;
            }
            int openParen = skipWhitespace(expression, afterName);
            if (openParen >= expression.length() || expression.charAt(openParen) != '(') {
                searchFrom = afterName;
                continue;
            }

            EnumMapCall call = enumMapCall(expression, openParen).orElse(null);
            if (call != null && tokenStart >= call.secondArgStart() && tokenEnd <= call.secondArgEnd()) {
                String secondArg = expression
                        .substring(call.secondArgStart(), call.secondArgEnd())
                        .strip();
                return secondArg.equals(tokenText);
            }
            searchFrom = afterName;
        }
        return false;
    }

    private Optional<EnumMapCall> enumMapCall(String expression, int openParen) {
        int depth = 0;
        int comma = -1;
        for (int i = openParen; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(expression, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    if (comma < 0) {
                        return Optional.empty();
                    }
                    return Optional.of(new EnumMapCall(comma + 1, i));
                }
            } else if (c == ',' && depth == 1 && comma < 0) {
                comma = i;
            }
        }
        return Optional.empty();
    }

    private boolean tokenBeforeKeyword(
            IlimapAnalysis analysis, IlimapTokenAtPosition token, int rangeStartOffset, String keyword) {
        int tokenStart = positionResolver.rangeStartOffset(analysis, token.range());
        int keywordOffset = findWholeWord(analysis.text(), keyword, rangeStartOffset);
        return keywordOffset >= 0 && tokenStart < keywordOffset;
    }

    private boolean tokenBetweenKeywords(
            IlimapAnalysis analysis,
            IlimapTokenAtPosition token,
            int rangeStartOffset,
            String firstKeyword,
            String secondKeyword) {
        int tokenStart = positionResolver.rangeStartOffset(analysis, token.range());
        int firstOffset = findWholeWord(analysis.text(), firstKeyword, rangeStartOffset);
        int secondOffset = firstOffset >= 0
                ? findWholeWord(analysis.text(), secondKeyword, firstOffset + firstKeyword.length())
                : -1;
        return firstOffset >= 0 && secondOffset >= 0 && tokenStart > firstOffset && tokenStart < secondOffset;
    }

    private boolean sameRange(IlimapAnalysis analysis, IlimapIdeRange left, IlimapIdeRange right) {
        return positionResolver.rangeStartOffset(analysis, left) == positionResolver.rangeStartOffset(analysis, right)
                && positionResolver.rangeEndOffset(analysis, left) == positionResolver.rangeEndOffset(analysis, right);
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

    private static int skipWhitespace(String text, int offset) {
        int current = offset;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
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

    private static boolean isWordBoundary(String text, int offset) {
        return offset < 0 || offset >= text.length() || !isIdentifierPart(text.charAt(offset));
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private record EnumMapCall(int secondArgStart, int secondArgEnd) {}
}
