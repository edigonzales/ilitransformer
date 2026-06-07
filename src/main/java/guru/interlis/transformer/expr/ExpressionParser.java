package guru.interlis.transformer.expr;

import java.util.ArrayList;
import java.util.List;

public final class ExpressionParser {

    private final String input;
    private int pos;

    private ExpressionParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    public static Expression parse(String expression) throws ExpressionParseException {
        if (expression == null || expression.isBlank()) {
            throw new ExpressionParseException("Expression is empty", expression, 0);
        }
        ExpressionParser parser = new ExpressionParser(expression.trim());
        Expression result = parser.parseExpression();
        parser.skipWhitespace();
        if (parser.pos < parser.input.length()) {
            throw new ExpressionParseException(
                    "Unexpected trailing characters: '" + parser.input.substring(parser.pos) + "'",
                    expression, parser.pos);
        }
        return result;
    }

    // -- Entry points ------------------------------------------------

    private Expression parseExpression() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw error("Unexpected end of expression");
        }
        Expression left = parseTopLevel();
        skipWhitespace();

        // Handle != null and == null comparisons
        if (pos + 1 < input.length()) {
            if (input.startsWith("!=", pos)) {
                pos += 2;
                skipWhitespace();
                if (!startsWithIgnoreCase("null")) {
                    throw error("Expected 'null' after '!='");
                }
                pos += 4;
                return new FunctionCallExpr("defined", List.of(left));
            }
            if (input.startsWith("==", pos)) {
                pos += 2;
                skipWhitespace();
                if (!startsWithIgnoreCase("null")) {
                    throw error("Expected 'null' after '=='");
                }
                pos += 4;
                return new FunctionCallExpr("notDefined", List.of(left));
            }
        }
        return left;
    }

    private Expression parseTopLevel() {
        char ch = peek();

        switch (ch) {
            case '"':
            case '\'':
                return parseStringLiteral();
            case '#':
                return parseEnumLiteral();
            case '$':
                return parsePathRef();
            default:
                // Could be: number, boolean, null, identifier, function call, if()
                if (ch == '-' || ch == '+' || Character.isDigit(ch) || (ch == '.' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
                    return parseNumberLiteral();
                }
                if (startsWithIgnoreCase("null")) {
                    pos += 4;
                    return new LiteralExpr(NullValue.INSTANCE);
                }
                if (startsWithIgnoreCase("true")) {
                    pos += 4;
                    return new LiteralExpr(BooleanValue.TRUE);
                }
                if (startsWithIgnoreCase("false")) {
                    pos += 5;
                    return new LiteralExpr(BooleanValue.FALSE);
                }
                // identifier-based: function call or bare path
                return parseIdentifierExpr();
        }
    }

    private Expression parseStringLiteral() {
        char quote = input.charAt(pos);
        pos++; // skip opening quote
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                pos += 2; // skip escaped char
            } else {
                pos++;
            }
        }
        if (pos >= input.length()) {
            throw error("Unterminated string literal");
        }
        String value = input.substring(start, pos);
        pos++; // skip closing quote
        return new LiteralExpr(new TextValue(value));
    }

    private Expression parseEnumLiteral() {
        pos++; // skip #
        int start = pos;
        while (pos < input.length() && Character.isJavaIdentifierPart(input.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            throw error("Empty enum literal");
        }
        String name = input.substring(start, pos);
        return new LiteralExpr(new EnumValue(name, null));
    }

    private Expression parseNumberLiteral() {
        int start = pos;
        if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
            pos++;
        }
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        double value = Double.parseDouble(input.substring(start, pos));
        return new LiteralExpr(new NumberValue(value));
    }

    private Expression parsePathRef() {
        if (!input.startsWith("${", pos)) {
            throw error("Expected '${' for path reference");
        }
        pos += 2; // skip ${
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != '.' && input.charAt(pos) != '}') {
            pos++;
        }
        if (pos == start || pos >= input.length()) {
            throw error("Invalid path reference: missing alias");
        }
        String alias = input.substring(start, pos);
        if (pos >= input.length() || input.charAt(pos) != '.') {
            throw error("Invalid path reference: missing '.' after alias");
        }
        pos++; // skip .
        start = pos;
        while (pos < input.length() && input.charAt(pos) != '}') {
            pos++;
        }
        if (pos == start) {
            throw error("Invalid path reference: missing attribute name");
        }
        String attrName = input.substring(start, pos);
        if (pos >= input.length()) {
            throw error("Unterminated path reference: missing '}'");
        }
        pos++; // skip }
        return new PathExpr(alias, attrName);
    }

    private Expression parseIdentifierExpr() {
        String name = parseIdentifier();
        skipWhitespace();

        if (pos < input.length() && input.charAt(pos) == '(') {
            // Function call
            return parseFunctionCall(name);
        }
        // Bare identifier – treat as literal or source path depending on content
        if (name.contains(".")) {
            String[] parts = name.split("\\.", 2);
            return new PathExpr(parts[0], parts[1]);
        }
        // Bare identifier – treat as source path (alias reference)
        return new PathExpr(name, null);
    }

    private String parseIdentifier() {
        int start = pos;
        while (pos < input.length() && (Character.isJavaIdentifierPart(input.charAt(pos)) || input.charAt(pos) == '.')) {
            pos++;
        }
        if (pos == start) {
            throw error("Expected identifier");
        }
        return input.substring(start, pos);
    }

    private Expression parseFunctionCall(String functionName) {
        pos++; // skip (
        List<Expression> args = new ArrayList<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) != ')') {
            args.add(parseExpression());
            skipWhitespace();
            while (pos < input.length() && input.charAt(pos) == ',') {
                pos++; // skip ,
                args.add(parseExpression());
                skipWhitespace();
            }
        }
        if (pos >= input.length() || input.charAt(pos) != ')') {
            throw error("Unterminated function call: missing ')'");
        }
        pos++; // skip )

        if ("if".equals(functionName)) {
            if (args.size() != 3) {
                throw error("if() requires exactly 3 arguments (condition, then, else)");
            }
            return new ConditionalExpr(args.get(0), args.get(1), args.get(2));
        }
        return new FunctionCallExpr(functionName, args);
    }

    // -- Helpers -----------------------------------------------------

    private char peek() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw error("Unexpected end of expression");
        }
        return input.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean startsWithIgnoreCase(String prefix) {
        if (pos + prefix.length() > input.length()) return false;
        String chunk = input.substring(pos, pos + prefix.length());
        if (!chunk.equalsIgnoreCase(prefix)) return false;
        // Ensure word boundary after
        if (pos + prefix.length() < input.length()
                && Character.isJavaIdentifierPart(input.charAt(pos + prefix.length()))) {
            return false;
        }
        return true;
    }

    private ExpressionParseException error(String message) {
        return new ExpressionParseException(message, input, pos);
    }
}
