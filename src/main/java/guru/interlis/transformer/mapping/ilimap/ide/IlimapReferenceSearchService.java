package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapIdentityStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapInputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapJoinStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapOutputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapWhereStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class IlimapReferenceSearchService {

    private final IlimapPositionResolver positionResolver;

    public IlimapReferenceSearchService() {
        this(new IlimapPositionResolver());
    }

    IlimapReferenceSearchService(IlimapPositionResolver positionResolver) {
        this.positionResolver = Objects.requireNonNull(positionResolver, "positionResolver");
    }

    public List<IlimapIdeRange> references(IlimapAnalysis analysis, IlimapResolvedSymbol resolved) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(resolved, "resolved");

        return switch (resolved.symbol().kind()) {
            case INPUT -> inputReferences(analysis, resolved.symbol().name());
            case OUTPUT -> outputReferences(analysis, resolved.symbol().name());
            case RULE -> ruleReferences(analysis, resolved.symbol().name());
            case ENUM_MAP -> enumMapReferences(analysis, resolved.symbol().name());
            case SOURCE_ALIAS -> scopedReferences(analysis, resolved, this::sourceAliasReferences);
            case JOIN_ALIAS -> scopedReferences(analysis, resolved, this::joinAliasReferences);
            case BAG -> scopedReferences(analysis, resolved, this::bagReferences);
            case REF -> scopedReferences(analysis, resolved, this::refReferences);
        };
    }

    private List<IlimapIdeRange> scopedReferences(
            IlimapAnalysis analysis,
            IlimapResolvedSymbol resolved,
            ScopedReferenceCollector collector) {
        return currentRuleForSymbol(analysis, resolved)
                .map(rule -> collector.references(analysis, rule, resolved.symbol().name()))
                .orElse(List.of());
    }

    @FunctionalInterface
    private interface ScopedReferenceCollector {
        List<IlimapIdeRange> references(IlimapAnalysis analysis, IlimapRuleBlock scope, String name);
    }

    public List<IlimapIdeRange> inputReferences(IlimapAnalysis analysis, String inputId) {
        if (!analysis.hasDocument() || inputId == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        inputDeclarationRange(analysis, inputId).ifPresent(ranges::add);

        for (IlimapRuleBlock rule : analysis.document().rules()) {
            for (IlimapRuleElement element : rule.elements()) {
                if (element instanceof IlimapSourceStmt source && source.inputIds().contains(inputId)) {
                    findIdentifierAfterKeyword(analysis, source.range(), "from", inputId)
                            .ifPresent(ranges::add);
                }
                if (element instanceof IlimapBagBlock bag) {
                    collectBagInputReferences(analysis, bag, inputId, ranges);
                }
            }
        }
        return List.copyOf(ranges);
    }

    public List<IlimapIdeRange> outputReferences(IlimapAnalysis analysis, String outputId) {
        if (!analysis.hasDocument() || outputId == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        outputDeclarationRange(analysis, outputId).ifPresent(ranges::add);

        for (IlimapRuleBlock rule : analysis.document().rules()) {
            for (IlimapRuleElement element : rule.elements()) {
                if (element instanceof IlimapTargetStmt target && outputId.equals(target.outputId())) {
                    findIdentifierAfterKeyword(analysis, target.range(), "target", outputId)
                            .ifPresent(ranges::add);
                }
            }
        }
        return List.copyOf(ranges);
    }

    public List<IlimapIdeRange> ruleReferences(IlimapAnalysis analysis, String ruleId) {
        if (!analysis.hasDocument() || ruleId == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        ruleDeclarationRange(analysis, ruleId).ifPresent(ranges::add);

        for (IlimapRuleBlock rule : analysis.document().rules()) {
            for (IlimapRuleElement element : rule.elements()) {
                if (element instanceof IlimapRefBlock ref && ruleId.equals(ref.targetRuleId())) {
                    findIdentifierAfterKeyword(analysis, ref.range(), "rule", ruleId)
                            .ifPresent(ranges::add);
                }
            }
        }
        return List.copyOf(ranges);
    }

    public List<IlimapIdeRange> enumMapReferences(IlimapAnalysis analysis, String enumMapId) {
        if (!analysis.hasDocument() || enumMapId == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        enumDeclarationRange(analysis, enumMapId).ifPresent(ranges::add);

        for (IlimapRuleBlock rule : analysis.document().rules()) {
            List<IlimapExpressionText> expressions = collectAllExpressions(rule);
            for (IlimapExpressionText expression : expressions) {
                collectEnumMapArgumentRanges(analysis, expression, enumMapId, ranges);
            }
        }
        return List.copyOf(ranges);
    }

    public List<IlimapIdeRange> sourceAliasReferences(
            IlimapAnalysis analysis, IlimapRuleBlock scopeRule, String alias) {
        if (!analysis.hasDocument() || scopeRule == null || alias == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        for (IlimapRuleElement element : scopeRule.elements()) {
            if (element instanceof IlimapSourceStmt source && alias.equals(source.alias())) {
                findIdentifierAfterKeyword(analysis, source.range(), "source", alias)
                        .ifPresent(ranges::add);
            }
        }

        List<IlimapExpressionText> expressions = collectAllExpressions(scopeRule);
        for (IlimapExpressionText expression : expressions) {
            collectAliasMemberAliasRanges(analysis, expression, alias, ranges);
        }

        return List.copyOf(ranges);
    }

    private List<IlimapIdeRange> joinAliasReferences(
            IlimapAnalysis analysis, IlimapRuleBlock scopeRule, String alias) {
        if (!analysis.hasDocument() || scopeRule == null || alias == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        for (IlimapRuleElement element : scopeRule.elements()) {
            if (element instanceof IlimapJoinStmt join && (alias.equals(join.leftAlias()) || alias.equals(join.rightAlias()))) {
                if (alias.equals(join.leftAlias())) {
                    joinLeftAliasDeclarationRange(analysis, join, alias).ifPresent(ranges::add);
                } else {
                    joinRightAliasDeclarationRange(analysis, join, alias).ifPresent(ranges::add);
                }
            }
        }

        List<IlimapExpressionText> expressions = collectAllExpressions(scopeRule);
        for (IlimapExpressionText expression : expressions) {
            collectAliasMemberAliasRanges(analysis, expression, alias, ranges);
        }

        return List.copyOf(ranges);
    }

    private List<IlimapIdeRange> bagReferences(
            IlimapAnalysis analysis, IlimapRuleBlock scopeRule, String bagId) {
        if (!analysis.hasDocument() || scopeRule == null || bagId == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        for (IlimapRuleElement element : scopeRule.elements()) {
            if (element instanceof IlimapBagBlock bag && bagId.equals(bag.id())) {
                findIdentifierAfterKeyword(analysis, bag.range(), "bag", bagId)
                        .ifPresent(ranges::add);
                break;
            }
        }
        return List.copyOf(ranges);
    }

    private List<IlimapIdeRange> refReferences(
            IlimapAnalysis analysis, IlimapRuleBlock scopeRule, String refId) {
        if (!analysis.hasDocument() || scopeRule == null || refId == null) {
            return List.of();
        }
        List<IlimapIdeRange> ranges = new ArrayList<>();

        for (IlimapRuleElement element : scopeRule.elements()) {
            if (element instanceof IlimapRefBlock ref && refId.equals(ref.id())) {
                findIdentifierAfterKeyword(analysis, ref.range(), "ref", refId)
                        .ifPresent(ranges::add);
                break;
            }
        }
        return List.copyOf(ranges);
    }

    private Optional<IlimapRuleBlock> currentRuleForSymbol(IlimapAnalysis analysis, IlimapResolvedSymbol resolved) {
        if (!analysis.hasDocument()) {
            return Optional.empty();
        }
        int offset = positionResolver.rangeStartOffset(analysis, resolved.range());
        return analysis.document().rules().stream()
                .filter(rule -> rule.range().start().offset() <= offset
                        && offset <= rule.range().end().offset())
                .findFirst();
    }

    private Optional<IlimapIdeRange> inputDeclarationRange(IlimapAnalysis analysis, String id) {
        if (!analysis.hasDocument()) {
            return Optional.empty();
        }
        for (IlimapInputBlock input : analysis.document().inputs()) {
            if (id.equals(input.id())) {
                return positionResolver.identifierRangeIn(analysis, input.range(), id);
            }
        }
        return Optional.empty();
    }

    private Optional<IlimapIdeRange> outputDeclarationRange(IlimapAnalysis analysis, String id) {
        if (!analysis.hasDocument()) {
            return Optional.empty();
        }
        for (IlimapOutputBlock output : analysis.document().outputs()) {
            if (id.equals(output.id())) {
                return positionResolver.identifierRangeIn(analysis, output.range(), id);
            }
        }
        return Optional.empty();
    }

    private Optional<IlimapIdeRange> ruleDeclarationRange(IlimapAnalysis analysis, String id) {
        if (!analysis.hasDocument()) {
            return Optional.empty();
        }
        for (IlimapRuleBlock rule : analysis.document().rules()) {
            if (id.equals(rule.id())) {
                return positionResolver.identifierRangeIn(analysis, rule.range(), id);
            }
        }
        return Optional.empty();
    }

    private Optional<IlimapIdeRange> enumDeclarationRange(IlimapAnalysis analysis, String id) {
        if (!analysis.hasDocument()) {
            return Optional.empty();
        }
        for (IlimapEnumBlock enumBlock : analysis.document().enums()) {
            if (id.equals(enumBlock.id())) {
                return positionResolver.identifierRangeIn(analysis, enumBlock.range(), id);
            }
        }
        return Optional.empty();
    }

    private Optional<IlimapIdeRange> joinLeftAliasDeclarationRange(
            IlimapAnalysis analysis, IlimapJoinStmt join, String alias) {
        int rangeStart = join.range().start().offset();
        int rangeEnd = join.range().end().offset();
        String text = analysis.text().substring(rangeStart, rangeEnd);
        int keywordEnd = text.indexOf(join.joinType());
        if (keywordEnd < 0) {
            return Optional.empty();
        }
        int afterKeyword = skipWhitespace(text, keywordEnd + join.joinType().length());
        if (afterKeyword >= text.length() || !text.startsWith(alias, afterKeyword)) {
            return Optional.empty();
        }
        return Optional.of(offsetRange(analysis, rangeStart + afterKeyword,
                rangeStart + afterKeyword + alias.length()));
    }

    private Optional<IlimapIdeRange> joinRightAliasDeclarationRange(
            IlimapAnalysis analysis, IlimapJoinStmt join, String alias) {
        int rangeStart = join.range().start().offset();
        int rangeEnd = join.range().end().offset();
        String text = analysis.text().substring(rangeStart, rangeEnd);
        int keywordEnd = text.indexOf(join.joinType());
        if (keywordEnd < 0) {
            return Optional.empty();
        }
        int afterKeyword = skipWhitespace(text, keywordEnd + join.joinType().length());
        int leftEnd = skipIdentifier(text, afterKeyword);
        int afterLeft = skipWhitespace(text, leftEnd);
        if (afterLeft >= text.length() || !text.startsWith(alias, afterLeft)) {
            return Optional.empty();
        }
        return Optional.of(offsetRange(analysis, rangeStart + afterLeft,
                rangeStart + afterLeft + alias.length()));
    }

    private void collectBagInputReferences(
            IlimapAnalysis analysis, IlimapBagBlock bag, String inputId, List<IlimapIdeRange> ranges) {
        if (bag.from() != null && inputId.equals(bag.from().inputId())) {
            findIdentifierAtEnd(analysis, bag.from().range(), inputId).ifPresent(ranges::add);
        }
        if (bag.nestedBags() != null) {
            for (IlimapBagBlock nested : bag.nestedBags()) {
                collectBagInputReferences(analysis, nested, inputId, ranges);
            }
        }
    }

    private Optional<IlimapIdeRange> findIdentifierAfterKeyword(
            IlimapAnalysis analysis, IlimapSourceRange searchRange, String keyword, String identifier) {
        int rangeStart = searchRange.start().offset();
        int rangeEnd = searchRange.end().offset();
        int searchFrom = rangeStart;
        String text = analysis.text();

        while (searchFrom < rangeEnd) {
            int kwIndex = text.indexOf(keyword, searchFrom);
            if (kwIndex < 0 || kwIndex >= rangeEnd) {
                break;
            }
            if (!isWordBoundary(text, kwIndex - 1) || !isWordBoundary(text, kwIndex + keyword.length())) {
                searchFrom = kwIndex + keyword.length();
                continue;
            }
            int afterKw = skipWhitespace(text, kwIndex + keyword.length());
            if (afterKw >= rangeEnd) {
                break;
            }
            if (text.startsWith(identifier, afterKw)
                    && isWordBoundary(text, afterKw + identifier.length())) {
                return Optional.of(offsetRange(analysis, afterKw, afterKw + identifier.length()));
            }
            searchFrom = afterKw;
        }
        return Optional.empty();
    }

    private Optional<IlimapIdeRange> findIdentifierAtEnd(
            IlimapAnalysis analysis, IlimapSourceRange searchRange, String identifier) {
        int rangeStart = searchRange.start().offset();
        int rangeEnd = searchRange.end().offset();
        String text = analysis.text().substring(rangeStart, rangeEnd);

        int lastIndex = -1;
        int searchFrom = 0;
        while (true) {
            int idx = text.indexOf(identifier, searchFrom);
            if (idx < 0) {
                break;
            }
            if (isWordBoundary(text, idx - 1) && isWordBoundary(text, idx + identifier.length())) {
                lastIndex = idx;
            }
            searchFrom = idx + identifier.length();
        }
        if (lastIndex >= 0) {
            return Optional.of(offsetRange(analysis, rangeStart + lastIndex,
                    rangeStart + lastIndex + identifier.length()));
        }
        return Optional.empty();
    }

    private void collectEnumMapArgumentRanges(
            IlimapAnalysis analysis, IlimapExpressionText expression, String enumMapId, List<IlimapIdeRange> ranges) {
        if (expression == null || expression.text() == null) {
            return;
        }
        int baseOffset = expression.range() != null ? expression.range().start().offset() : 0;
        String text = expression.text();
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int callStart = text.indexOf("enumMap", searchFrom);
            if (callStart < 0) {
                break;
            }
            int afterName = callStart + "enumMap".length();
            if (!isWordBoundary(text, callStart - 1) || !isWordBoundary(text, afterName)) {
                searchFrom = afterName;
                continue;
            }
            int openParen = skipWhitespace(text, afterName);
            if (openParen >= text.length() || text.charAt(openParen) != '(') {
                searchFrom = afterName;
                continue;
            }
            Optional<int[]> secondArg = enumMapSecondArg(text, openParen);
            if (secondArg.isPresent()) {
                int argStart = secondArg.get()[0];
                int argEnd = secondArg.get()[1];
                String arg = text.substring(argStart, argEnd).strip();
                boolean isQuoted = arg.length() >= 2
                        && (arg.charAt(0) == '"' || arg.charAt(0) == '\'')
                        && arg.charAt(arg.length() - 1) == arg.charAt(0);
                if (!isQuoted && arg.equals(enumMapId)) {
                    String unquoted = arg;
                    int actualStart = text.indexOf(unquoted.strip(), argStart);
                    if (actualStart >= 0) {
                        ranges.add(offsetRange(analysis, baseOffset + actualStart,
                                baseOffset + actualStart + unquoted.strip().length()));
                    }
                }
            }
            searchFrom = afterName;
        }
    }

    private Optional<int[]> enumMapSecondArg(String expression, int openParen) {
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
                    return Optional.of(new int[]{comma + 1, i});
                }
            } else if (c == ',' && depth == 1 && comma < 0) {
                comma = i;
            }
        }
        return Optional.empty();
    }

    private void collectAliasMemberAliasRanges(
            IlimapAnalysis analysis, IlimapExpressionText expression, String alias, List<IlimapIdeRange> ranges) {
        if (expression == null || expression.text() == null) {
            return;
        }
        int baseOffset = expression.range() != null ? expression.range().start().offset() : 0;
        String text = expression.text();
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int aliasIndex = text.indexOf(alias, searchFrom);
            if (aliasIndex < 0) {
                break;
            }
            boolean leftBoundary = aliasIndex == 0 || isWordBoundary(text, aliasIndex - 1);
            int afterAlias = aliasIndex + alias.length();
            boolean rightDot = afterAlias < text.length() && text.charAt(afterAlias) == '.';
            if (leftBoundary && rightDot) {
                ranges.add(offsetRange(analysis, baseOffset + aliasIndex,
                        baseOffset + afterAlias));
            }
            searchFrom = afterAlias;
        }
    }

    private List<IlimapExpressionText> collectAllExpressions(IlimapRuleBlock rule) {
        List<IlimapExpressionText> result = new ArrayList<>();
        for (IlimapRuleElement element : rule.elements()) {
            collectExpressionsFromElement(element, result);
        }
        return result;
    }

    private void collectExpressionsFromElement(IlimapRuleElement element, List<IlimapExpressionText> result) {
        if (element instanceof IlimapAssignmentBlock assignBlock) {
            for (IlimapAssignment assign : assignBlock.assignments()) {
                if (assign.expression() != null) {
                    result.add(assign.expression());
                }
            }
        } else if (element instanceof IlimapDefaultsBlock defaults) {
            for (IlimapAssignment assign : defaults.assignments()) {
                if (assign.expression() != null) {
                    result.add(assign.expression());
                }
            }
        } else if (element instanceof IlimapWhereStmt where && where.expression() != null) {
            result.add(where.expression());
        } else if (element instanceof IlimapIdentityStmt identity && identity.expressions() != null) {
            result.addAll(identity.expressions());
        } else if (element instanceof IlimapBagBlock bag) {
            collectExpressionsFromBag(bag, result);
        } else if (element instanceof IlimapRefBlock ref && ref.sourceRef() != null) {
            result.add(ref.sourceRef());
        } else if (element instanceof IlimapLossBlock loss) {
            if (loss.sourcePath() != null) {
                result.add(loss.sourcePath());
            }
            if (loss.when() != null) {
                result.add(loss.when());
            }
        } else if (element instanceof IlimapJoinStmt join && join.on() != null) {
            result.add(join.on());
        } else if (element instanceof IlimapSourceStmt source && source.where() != null) {
            result.add(source.where());
        }
    }

    private void collectExpressionsFromBag(IlimapBagBlock bag, List<IlimapExpressionText> result) {
        if (bag.where() != null) {
            result.add(bag.where());
        }
        if (bag.assign() != null && bag.assign().assignments() != null) {
            for (IlimapAssignment assign : bag.assign().assignments()) {
                if (assign.expression() != null) {
                    result.add(assign.expression());
                }
            }
        }
        if (bag.nestedBags() != null) {
            for (IlimapBagBlock nested : bag.nestedBags()) {
                collectExpressionsFromBag(nested, result);
            }
        }
    }

    private IlimapIdeRange offsetRange(IlimapAnalysis analysis, int startOffset, int endOffset) {
        IlimapIdePosition start = analysis.lineMap().toIdePosition(startOffset);
        IlimapIdePosition end = analysis.lineMap().toIdePosition(endOffset);
        return new IlimapIdeRange(start, end);
    }

    private static int skipWhitespace(String text, int offset) {
        int current = offset;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private static int skipIdentifier(String text, int offset) {
        int current = offset;
        while (current < text.length() && isIdentifierPart(text.charAt(current))) {
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
}
