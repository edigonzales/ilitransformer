package guru.interlis.transformer.expr;

import java.math.BigDecimal;
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
        Expression result = parser.parseOr();
        parser.skipWhitespace();
        if (parser.pos < parser.input.length()) {
            throw new ExpressionParseException(
                    "Unexpected trailing characters: '" + parser.input.substring(parser.pos) + "'",
                    expression, parser.pos);
        }
        return result;
    }

    // -- Precedence levels (lowest to highest) -----------------------

    private Expression parseOr() {
        Expression left = parseAnd();
        skipWhitespace();
        while (consumeKeyword("or")) {
            skipWhitespace();
            Expression right = parseAnd();
            left = new ConditionalExpr(left, new LiteralExpr(BooleanValue.TRUE), right);
            skipWhitespace();
        }
        return left;
    }

    private Expression parseAnd() {
        Expression left = parseEquality();
        skipWhitespace();
        while (consumeKeyword("and")) {
            skipWhitespace();
            Expression right = parseEquality();
            left = new ConditionalExpr(left, right, new LiteralExpr(BooleanValue.FALSE));
            skipWhitespace();
        }
        return left;
    }

    private Expression parseEquality() {
        Expression left = parseComparison();
        skipWhitespace();
        while (pos < input.length()) {
            if (input.startsWith("==", pos)) {
                pos += 2;
                skipWhitespace();
                Expression right = parseComparison();
                if (isNullLiteral(right)) {
                    left = new FunctionCallExpr("notDefined", List.of(left));
                } else {
                    left = new FunctionCallExpr("eq", List.of(left, right));
                }
            } else if (input.startsWith("!=", pos)) {
                pos += 2;
                skipWhitespace();
                Expression right = parseComparison();
                if (isNullLiteral(right)) {
                    left = new FunctionCallExpr("defined", List.of(left));
                } else {
                    left = new FunctionCallExpr("neq", List.of(left, right));
                }
            } else {
                break;
            }
            skipWhitespace();
        }
        return left;
    }

    private Expression parseComparison() {
        Expression left = parseUnary();
        skipWhitespace();
        while (pos < input.length()) {
            String op = null;
            if (input.startsWith("<=", pos)) {
                op = "lte";
                pos += 2;
            } else if (input.startsWith(">=", pos)) {
                op = "gte";
                pos += 2;
            } else if (input.startsWith("<", pos)) {
                op = "lt";
                pos += 1;
            } else if (input.startsWith(">", pos)) {
                op = "gt";
                pos += 1;
            } else {
                break;
            }
            skipWhitespace();
            Expression right = parseUnary();
            left = new FunctionCallExpr(op, List.of(left, right));
            skipWhitespace();
        }
        return left;
    }

    private Expression parseUnary() {
        skipWhitespace();
        if (consumeKeyword("not")) {
            skipWhitespace();
            Expression operand = parseUnary();
            return new FunctionCallExpr("not", List.of(operand));
        }
        return parsePrimary();
    }

    private Expression parsePrimary() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw error("Unexpected end of expression");
        }

        char ch = input.charAt(pos);

        switch (ch) {
            case '(':
                pos++;
                Expression inner = parseOr();
                skipWhitespace();
                if (pos >= input.length() || input.charAt(pos) != ')') {
                    throw error("Unterminated parenthesized expression");
                }
                pos++;
                return inner;
            case '"':
            case '\'':
                return parseStringLiteral();
            case '#':
                return parseEnumLiteral();
            case '$':
                return parsePathRef();
            default:
                if (ch == '-' || ch == '+' || Character.isDigit(ch)
                        || (ch == '.' && pos + 1 < input.length()
                            && Character.isDigit(input.charAt(pos + 1)))) {
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
                return parseIdentifierExpr();
        }
    }

    // -- Literals ----------------------------------------------------

    private Expression parseStringLiteral() {
        char quote = input.charAt(pos);
        pos++;
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                pos += 2;
            } else {
                pos++;
            }
        }
        if (pos >= input.length()) {
            throw error("Unterminated string literal");
        }
        String value = input.substring(start, pos);
        pos++;
        return new LiteralExpr(new TextValue(value));
    }

    private Expression parseEnumLiteral() {
        pos++;
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
        BigDecimal value = new BigDecimal(input.substring(start, pos));
        return new LiteralExpr(new NumberValue(value));
    }

    // -- Path reference ----------------------------------------------

    private Expression parsePathRef() {
        if (!input.startsWith("${", pos)) {
            throw error("Expected '${' for path reference");
        }
        pos += 2;
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
        pos++;
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
        pos++;
        return new PathExpr(alias, attrName);
    }

    // -- Identifier expr (function call or bare path) ----------------

    private Expression parseIdentifierExpr() {
        String name = parseIdentifier();
        skipWhitespace();

        if (pos < input.length() && input.charAt(pos) == '(') {
            return parseFunctionCall(name);
        }
        if (name.contains(".")) {
            String[] parts = name.split("\\.", 2);
            return new PathExpr(parts[0], parts[1]);
        }
        return new PathExpr(name, null);
    }

    private String parseIdentifier() {
        int start = pos;
        while (pos < input.length()
                && (Character.isJavaIdentifierPart(input.charAt(pos)) || input.charAt(pos) == '.')) {
            pos++;
        }
        if (pos == start) {
            throw error("Expected identifier");
        }
        return input.substring(start, pos);
    }

    private Expression parseFunctionCall(String functionName) {
        pos++;
        List<Expression> args = new ArrayList<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) != ')') {
            args.add(parseOr());
            skipWhitespace();
            while (pos < input.length() && input.charAt(pos) == ',') {
                pos++;
                args.add(parseOr());
                skipWhitespace();
            }
        }
        if (pos >= input.length() || input.charAt(pos) != ')') {
            throw error("Unterminated function call: missing ')'");
        }
        pos++;

        if ("if".equals(functionName)) {
            if (args.size() != 3) {
                throw error("if() requires exactly 3 arguments (condition, then, else)");
            }
            return new ConditionalExpr(args.get(0), args.get(1), args.get(2));
        }
        return new FunctionCallExpr(functionName, args);
    }

    // -- Helpers -----------------------------------------------------

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean startsWithIgnoreCase(String prefix) {
        if (pos + prefix.length() > input.length()) return false;
        String chunk = input.substring(pos, pos + prefix.length());
        if (!chunk.equalsIgnoreCase(prefix)) return false;
        if (pos + prefix.length() < input.length()
                && Character.isJavaIdentifierPart(input.charAt(pos + prefix.length()))) {
            return false;
        }
        return true;
    }

    private boolean consumeKeyword(String keyword) {
        if (startsWithIgnoreCase(keyword)) {
            pos += keyword.length();
            return true;
        }
        return false;
    }

    private static boolean isNullLiteral(Expression expr) {
        return expr instanceof LiteralExpr lit && lit.value() instanceof NullValue;
    }

    private ExpressionParseException error(String message) {
        return new ExpressionParseException(message, input, pos);
    }
}
