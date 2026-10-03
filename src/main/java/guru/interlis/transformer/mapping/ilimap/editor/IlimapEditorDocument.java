package guru.interlis.transformer.mapping.ilimap.editor;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapIdentifierRules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Source-preserving edits shared by graphical editors. No UI dependencies, no whole-file formatter. */
public final class IlimapEditorDocument {
    private String text;
    private long revision;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private static final int MAX_UNDO_STEPS = 200;

    public IlimapEditorDocument(String text) {
        this.text = Objects.requireNonNull(text);
    }

    public String text() {
        return text;
    }

    public long revision() {
        return revision;
    }

    public IlimapDocument document() {
        return new IlimapParser(text).parseDocument();
    }

    public void setText(String value) {
        Objects.requireNonNull(value);
        if (text.equals(value)) return;
        undo.push(text);
        while (undo.size() > MAX_UNDO_STEPS) undo.removeLast();
        redo.clear();
        text = value;
        revision++;
    }
    /** Commit a graphical command as one undo step; failed commands leave no partial edits. */
    public void atomicEdit(Runnable operation) {
        String before = text;
        long beforeRevision = revision;
        Deque<String> previousUndo = new ArrayDeque<>(undo), previousRedo = new ArrayDeque<>(redo);
        try {
            operation.run();
            if (!text.equals(before)) {
                undo.clear();
                undo.addAll(previousUndo);
                undo.push(before);
                redo.clear();
            }
        } catch (RuntimeException ex) {
            text = before;
            revision = beforeRevision;
            undo.clear();
            undo.addAll(previousUndo);
            redo.clear();
            redo.addAll(previousRedo);
            throw ex;
        }
    }

    public void undo() {
        if (!undo.isEmpty()) {
            redo.push(text);
            text = undo.pop();
            revision++;
        }
    }

    public void redo() {
        if (!redo.isEmpty()) {
            undo.push(text);
            text = redo.pop();
            revision++;
        }
    }

    public void edit(long expectedRevision, int start, int end, String replacement) {
        if (revision != expectedRevision)
            throw new IllegalStateException("Mapping changed; refresh before applying this edit");
        if (start < 0 || end < start || end > text.length()) throw new IllegalArgumentException("Invalid edit range");
        String candidate = text.substring(0, start) + replacement + text.substring(end);
        new IlimapParser(candidate).parseDocument();
        setText(candidate);
    }

    public IlimapRuleBlock rule(String id) {
        return document().rules().stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown rule: " + id));
    }

    public void insertRuleElement(String ruleId, String snippet) {
        var rule = rule(ruleId);
        int end = closingBrace(rule.range().end().offset());
        edit(revision, end, end, "\n" + snippet + "\n");
    }

    public void insertTopLevel(String snippet) {
        int end = closingBrace(document().range().end().offset());
        edit(revision, end, end, "\n" + snippet + "\n");
    }

    /** Keep the stable rule ID and all other rule contents, including comments. */
    public void targetClass(String ruleId, String targetClass) {
        var target = rule(ruleId).elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .findFirst()
                .orElseThrow();
        var token = tokens(target.range()).stream()
                .filter(t -> t.type() == guru.interlis.transformer.mapping.ilimap.lexer.IlimapTokenType.STRING)
                .findFirst()
                .orElseThrow();
        edit(revision, token.range().start().offset(), token.range().end().offset(), quote(targetClass));
    }

    public void reference(String ruleId, String role, String targetRule, String sourceExpression) {
        var existing = rule(ruleId).elements().stream()
                .filter(IlimapRefBlock.class::isInstance)
                .map(IlimapRefBlock.class::cast)
                .filter(r -> role.equals(r.role()))
                .toList();
        if (existing.size() > 1) throw new IllegalArgumentException("Several reference blocks for role " + role);
        if (existing.isEmpty()) {
            insertRuleElement(
                    ruleId,
                    "    ref " + role + " { role " + quote(role) + "; target rule " + targetRule + " sourceRef "
                            + sourceExpression + "; }");
            return;
        }
        var ref = existing.getFirst();
        if (ref.association() != null || ref.sourceRef() == null || ref.targetRuleId() == null)
            throw new IllegalArgumentException("Edit this advanced reference definition in the DSL");
        atomicEdit(() -> {
            var expression = ref.sourceRef().range();
            edit(revision, expression.start().offset(), expression.end().offset(), sourceExpression);
            var current = rule(ruleId).elements().stream()
                    .filter(IlimapRefBlock.class::isInstance)
                    .map(IlimapRefBlock.class::cast)
                    .filter(r -> role.equals(r.role()))
                    .findFirst()
                    .orElseThrow();
            var lexer = new guru.interlis.transformer.mapping.ilimap.lexer.IlimapLexer(text);
            lexer.skipTo(current.range().start().offset());
            while (lexer.hasNext()
                    && lexer.peek().range().start().offset()
                            < current.range().end().offset()) {
                if (lexer.next().isKeyword("rule")) {
                    var range = lexer.next().range();
                    edit(revision, range.start().offset(), range.end().offset(), targetRule);
                    return;
                }
            }
            throw new IllegalArgumentException("Reference target rule is missing");
        });
    }

    private List<guru.interlis.transformer.mapping.ilimap.lexer.IlimapToken> tokens(
            guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange range) {
        var lexer = new guru.interlis.transformer.mapping.ilimap.lexer.IlimapLexer(text);
        lexer.skipTo(range.start().offset());
        var result = new java.util.ArrayList<guru.interlis.transformer.mapping.ilimap.lexer.IlimapToken>();
        while (lexer.hasNext()
                && lexer.peek().range().start().offset() < range.end().offset()) result.add(lexer.next());
        return result;
    }

    public void assign(String ruleId, String structurePath, String target, String expression) {
        if (!IlimapIdentifierRules.isValidAliasId(target))
            throw new IllegalArgumentException("Invalid target attribute: " + target);
        var rule = rule(ruleId);
        IlimapAssignmentBlock block = null;
        int scopeEnd = rule.range().end().offset();
        if (structurePath == null || structurePath.isBlank()) {
            block = rule.elements().stream()
                    .filter(IlimapAssignmentBlock.class::isInstance)
                    .map(IlimapAssignmentBlock.class::cast)
                    .findFirst()
                    .orElse(null);
        } else {
            List<IlimapBagBlock> bags = rule.elements().stream()
                    .filter(IlimapBagBlock.class::isInstance)
                    .map(IlimapBagBlock.class::cast)
                    .toList();
            for (String component : structurePath.split("/")) {
                var bag = bags.stream()
                        .filter(b -> b.id().equals(component))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown structure context: " + structurePath));
                block = bag.assign();
                scopeEnd = bag.range().end().offset();
                bags = bag.nestedBags();
            }
        }
        // Parse the expression in its actual DSL context, including aliases and enum syntax.
        if (block != null) {
            var found = block.assignments().stream()
                    .filter(a -> a.targetAttribute().equals(target))
                    .findFirst();
            if (found.isPresent()) {
                var range = found.get().expression().range();
                edit(revision, range.start().offset(), range.end().offset(), expression);
            } else {
                int end = closingBrace(block.range().end().offset());
                edit(revision, end, end, "\n      " + target + " = " + expression + ";\n    ");
            }
        } else {
            int end = closingBrace(scopeEnd);
            edit(revision, end, end, "\n    assign { " + target + " = " + expression + "; }\n  ");
        }
    }

    /** Add a strict value table and its assignment as one source-preserving command. */
    public void enumeration(
            String ruleId,
            String structurePath,
            String target,
            String sourceExpression,
            String tableId,
            java.util.Map<String, String> values) {
        if (!IlimapIdentifierRules.isValidAliasId(tableId) || values.isEmpty())
            throw new IllegalArgumentException("A named, non-empty enumeration table is required");
        rule(ruleId);
        atomicEdit(() -> {
            int offset = document().rules().getFirst().range().start().offset();
            int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
            String indent = text.substring(lineStart, offset);
            if (!indent.isBlank()) indent = "";
            StringBuilder snippet = new StringBuilder("enum " + tableId + " {\n");
            for (var value : values.entrySet())
                snippet.append(indent)
                        .append("  ")
                        .append(quote(value.getKey()))
                        .append(" => ")
                        .append(quote(value.getValue()))
                        .append(";\n");
            snippet.append(indent).append("}\n").append(indent);
            edit(revision, offset, offset, snippet.toString());
            // Strict/default variants take a string table name, unlike enumMap's DSL shorthand.
            assign(ruleId, structurePath, target, "enumMapStrict(" + sourceExpression + ", " + quote(tableId) + ")");
        });
    }

    private int closingBrace(int end) {
        int offset = Math.min(end, text.length()) - 1;
        while (offset >= 0 && Character.isWhitespace(text.charAt(offset))) offset--;
        if (offset < 0 || text.charAt(offset) != '}') throw new IllegalStateException("Missing closing brace");
        return offset;
    }

    public static String quote(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r") + "\"";
    }
}
