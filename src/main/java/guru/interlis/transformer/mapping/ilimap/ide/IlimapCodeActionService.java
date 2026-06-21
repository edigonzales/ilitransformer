package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapCreateBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapIdentityStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapJoinStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapWhereStmt;
import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapIdentifierRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class IlimapCodeActionService {

    public static final String QUICK_FIX = "quickfix";
    public static final String SOURCE = "source";

    private static final String USE_SYMBOLIC_ENUM_MAP_TITLE = "Use symbolic enum map reference";
    private static final String FORMAT_DOCUMENT_TITLE = "Format ILIMAP document";

    private final IlimapFormattingService formattingService;

    public IlimapCodeActionService() {
        this(new IlimapFormattingService());
    }

    public IlimapCodeActionService(IlimapFormattingService formattingService) {
        this.formattingService = Objects.requireNonNull(formattingService, "formattingService");
    }

    public List<IlimapCodeAction> codeActions(IlimapAnalysis analysis, IlimapIdeRange range) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(range, "range");

        List<IlimapCodeAction> actions = new ArrayList<>();
        if (analysis.hasDocument()) {
            for (IlimapExpressionText expression : expressions(analysis.document())) {
                actions.addAll(enumMapCodeActions(analysis, range, expression));
            }
        }
        formattingService
                .format(analysis.uri(), analysis.text(), IlimapFormatOptions.defaults())
                .map(edit -> new IlimapCodeAction(FORMAT_DOCUMENT_TITLE, SOURCE, List.of(edit), null))
                .ifPresent(actions::add);
        return actions;
    }

    private List<IlimapCodeAction> enumMapCodeActions(
            IlimapAnalysis analysis, IlimapIdeRange requestedRange, IlimapExpressionText expression) {
        int expressionStart = expression.range().start().offset();
        int expressionEnd = expression.range().end().offset();
        String text = analysis.text().substring(expressionStart, expressionEnd);

        List<IlimapCodeAction> actions = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int enumMapStart = nextEnumMapFunction(text, searchFrom);
            if (enumMapStart < 0) {
                break;
            }
            int afterName = enumMapStart + "enumMap".length();
            searchFrom = afterName;
            if (!isWordBoundary(text, enumMapStart - 1) || !isWordBoundary(text, afterName)) {
                continue;
            }
            int openParen = skipWhitespace(text, afterName);
            if (openParen >= text.length() || text.charAt(openParen) != '(') {
                continue;
            }
            enumMapCall(text, openParen)
                    .filter(call -> overlaps(
                            analysis,
                            requestedRange,
                            expressionStart + call.argumentStart(),
                            expressionStart + call.argumentEnd()))
                    .flatMap(call -> toCodeAction(analysis, expressionStart, text, call))
                    .ifPresent(actions::add);
        }
        return actions;
    }

    private Optional<IlimapCodeAction> toCodeAction(
            IlimapAnalysis analysis, int expressionStart, String expressionText, EnumMapCall call) {
        String argument = expressionText.substring(call.argumentStart(), call.argumentEnd());
        if (isDoubleQuoted(argument)) {
            return stringReferenceCodeAction(analysis, expressionStart, call, argument);
        }
        return missingEnumMapCodeAction(analysis, argument);
    }

    private Optional<IlimapCodeAction> stringReferenceCodeAction(
            IlimapAnalysis analysis, int expressionStart, EnumMapCall call, String argument) {
        String enumMapId = argument.substring(1, argument.length() - 1);
        if (analysis.symbols().resolveEnumMap(enumMapId).isEmpty()) {
            return Optional.empty();
        }

        int firstQuote = expressionStart + call.argumentStart();
        int secondQuote = expressionStart + call.argumentEnd() - 1;
        return Optional.of(new IlimapCodeAction(
                USE_SYMBOLIC_ENUM_MAP_TITLE,
                QUICK_FIX,
                List.of(
                        emptyEdit(analysis, firstQuote, firstQuote + 1),
                        emptyEdit(analysis, secondQuote, secondQuote + 1)),
                DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF));
    }

    private Optional<IlimapCodeAction> missingEnumMapCodeAction(IlimapAnalysis analysis, String enumMapId) {
        if (!IlimapIdentifierRules.isValidSymbolId(enumMapId)
                || analysis.symbols().resolveEnumMap(enumMapId).isPresent()) {
            return Optional.empty();
        }

        int insertionOffset = insertionOffset(analysis);
        return Optional.of(new IlimapCodeAction(
                "Create enum map '" + enumMapId + "'",
                QUICK_FIX,
                List.of(new IlimapTextEdit(
                        insertionRange(analysis, insertionOffset),
                        enumBlockText(analysis, enumMapId, insertionOffset))),
                DiagnosticCode.ILIMAP_UNKNOWN_ENUM_MAP));
    }

    private IlimapTextEdit emptyEdit(IlimapAnalysis analysis, int startOffset, int endOffset) {
        return new IlimapTextEdit(
                new IlimapIdeRange(
                        analysis.lineMap().toIdePosition(startOffset),
                        analysis.lineMap().toIdePosition(endOffset)),
                "");
    }

    private IlimapIdeRange insertionRange(IlimapAnalysis analysis, int offset) {
        IlimapIdePosition position = analysis.lineMap().toIdePosition(offset);
        return new IlimapIdeRange(position, position);
    }

    private int insertionOffset(IlimapAnalysis analysis) {
        IlimapDocument document = analysis.document();
        if (!document.enums().isEmpty()) {
            IlimapEnumBlock lastEnum = document.enums().get(document.enums().size() - 1);
            return lineStartAfter(analysis.text(), lastEnum.range().end().offset());
        }
        if (!document.rules().isEmpty()) {
            int ruleLine = document.rules().get(0).range().start().line() - 1;
            return analysis.lineMap().positionToOffset(ruleLine, 0);
        }
        return Math.max(0, document.range().end().offset() - 1);
    }

    private int lineStartAfter(String text, int offset) {
        int newline = text.indexOf('\n', offset);
        return newline < 0 ? offset : newline + 1;
    }

    private String enumBlockText(IlimapAnalysis analysis, String enumMapId, int offset) {
        String text = "  enum " + enumMapId + " {\n  }\n";
        if (isBlankLineAt(analysis.text(), offset)) {
            return text;
        }
        return text + "\n";
    }

    private boolean isBlankLineAt(String text, int offset) {
        if (offset >= text.length()) {
            return false;
        }
        int lineEnd = text.indexOf('\n', offset);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        return text.substring(offset, lineEnd).isBlank();
    }

    private Optional<EnumMapCall> enumMapCall(String text, int openParen) {
        int depth = 0;
        int comma = -1;
        for (int i = openParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(text, i);
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                i = skipLineComment(text, i);
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i = skipBlockComment(text, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    if (comma < 0) {
                        return Optional.empty();
                    }
                    return trimmedArgument(text, comma + 1, i);
                }
            } else if (c == ',' && depth == 1 && comma < 0) {
                comma = i;
            }
        }
        return Optional.empty();
    }

    private int nextEnumMapFunction(String text, int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(text, i);
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                i = skipLineComment(text, i);
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i = skipBlockComment(text, i);
                continue;
            }
            if (text.startsWith("enumMap", i)
                    && isWordBoundary(text, i - 1)
                    && isWordBoundary(text, i + "enumMap".length())) {
                return i;
            }
        }
        return -1;
    }

    private Optional<EnumMapCall> trimmedArgument(String text, int start, int end) {
        int argumentStart = start;
        while (argumentStart < end && Character.isWhitespace(text.charAt(argumentStart))) {
            argumentStart++;
        }
        int argumentEnd = end;
        while (argumentEnd > argumentStart && Character.isWhitespace(text.charAt(argumentEnd - 1))) {
            argumentEnd--;
        }
        if (argumentStart == argumentEnd) {
            return Optional.empty();
        }
        return Optional.of(new EnumMapCall(argumentStart, argumentEnd));
    }

    private boolean overlaps(IlimapAnalysis analysis, IlimapIdeRange range, int startOffset, int endOffset) {
        int rangeStart = analysis.lineMap()
                .positionToOffset(range.start().line(), range.start().character());
        int rangeEnd = analysis.lineMap()
                .positionToOffset(range.end().line(), range.end().character());
        if (rangeStart == rangeEnd) {
            return rangeStart >= startOffset && rangeStart <= endOffset;
        }
        return rangeStart <= endOffset && rangeEnd >= startOffset;
    }

    private List<IlimapExpressionText> expressions(IlimapDocument document) {
        List<IlimapExpressionText> result = new ArrayList<>();
        if (document.defaults() != null) {
            document.defaults().assignments().forEach(assignment -> result.add(assignment.expression()));
        }
        for (var rule : document.rules()) {
            for (IlimapRuleElement element : rule.elements()) {
                collectExpressions(element, result);
            }
        }
        return result;
    }

    private void collectExpressions(IlimapRuleElement element, List<IlimapExpressionText> result) {
        switch (element) {
            case IlimapAssignmentBlock assign ->
                assign.assignments().forEach(assignment -> result.add(assignment.expression()));
            case IlimapDefaultsBlock defaults ->
                defaults.assignments().forEach(assignment -> result.add(assignment.expression()));
            case IlimapSourceStmt source -> {
                if (source.where() != null) {
                    result.add(source.where());
                }
            }
            case IlimapWhereStmt where -> result.add(where.expression());
            case IlimapIdentityStmt identity -> result.addAll(identity.expressions());
            case IlimapJoinStmt join -> result.add(join.on());
            case IlimapBagBlock bag -> collectBagExpressions(bag, result);
            case IlimapRefBlock ref -> {
                if (ref.sourceRef() != null) {
                    result.add(ref.sourceRef());
                }
            }
            case IlimapCreateBlock create -> {
                if (create.assign() != null) {
                    create.assign().assignments().forEach(assignment -> result.add(assignment.expression()));
                }
            }
            case IlimapLossBlock loss -> {
                if (loss.sourcePath() != null) {
                    result.add(loss.sourcePath());
                }
                if (loss.when() != null) {
                    result.add(loss.when());
                }
            }
            default -> {}
        }
    }

    private void collectBagExpressions(IlimapBagBlock bag, List<IlimapExpressionText> result) {
        if (bag.from() != null && bag.from().where() != null) {
            result.add(bag.from().where());
        }
        if (bag.assign() != null) {
            bag.assign().assignments().forEach(assignment -> result.add(assignment.expression()));
        }
        for (IlimapBagBlock nestedBag : bag.nestedBags()) {
            collectBagExpressions(nestedBag, result);
        }
    }

    private static boolean isDoubleQuoted(String text) {
        return text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"';
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

    private static int skipLineComment(String text, int slashOffset) {
        int newline = text.indexOf('\n', slashOffset + 2);
        return newline < 0 ? text.length() - 1 : newline;
    }

    private static int skipBlockComment(String text, int slashOffset) {
        int end = text.indexOf("*/", slashOffset + 2);
        return end < 0 ? text.length() - 1 : end + 1;
    }

    private static boolean isWordBoundary(String text, int offset) {
        return offset < 0 || offset >= text.length() || !isIdentifierPart(text.charAt(offset));
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private record EnumMapCall(int argumentStart, int argumentEnd) {}
}
