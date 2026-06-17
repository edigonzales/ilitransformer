package guru.interlis.transformer.mapping.ilimap.parser;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.*;

import java.util.ArrayList;
import java.util.List;

public final class IlimapParser {

    private final String source;
    private final IlimapLexer lexer;
    private final IlimapExpressionReader expressionReader;
    private IlimapToken lastConsumed;

    public IlimapParser(String source) {
        this.source = source;
        this.lexer = new IlimapLexer(source);
        this.expressionReader = new IlimapExpressionReader();
    }

    public IlimapDocument parseDocument() {
        IlimapSourcePosition docStart = currentToken().range().start();

        expectKeyword("mapping");
        IlimapFormatVersion formatVersion = parseFormatVersion();
        String name = parseOptionalString();
        expectToken(IlimapTokenType.LBRACE);

        IlimapJobBlock job = peekKeyword("job") ? parseJobBlock() : null;

        List<IlimapInputBlock> inputs = new ArrayList<>();
        while (peekKeyword("input")) {
            inputs.add(parseInputBlock());
        }

        List<IlimapOutputBlock> outputs = new ArrayList<>();
        while (peekKeyword("output")) {
            outputs.add(parseOutputBlock());
        }

        IlimapOidBlock oid =
                peekKeyword("oid") ? parseOidBlock() : null;
        IlimapBasketStmt basket =
                peekKeyword("basket") ? parseBasketStmt() : null;

        List<IlimapEnumBlock> enums = new ArrayList<>();
        while (peekKeyword("enum")) {
            enums.add(parseEnumBlock());
        }

        IlimapDefaultsBlock defaults =
                peekKeyword("defaults") ? parseTopLevelDefaults() : null;

        List<IlimapRuleBlock> rules = new ArrayList<>();
        while (peekKeyword("rule")) {
            rules.add(parseRuleBlock());
        }

        IlimapSourcePosition rbracePos = expectToken(IlimapTokenType.RBRACE).range().end();
        expectToken(IlimapTokenType.EOF);

        IlimapSourceRange range = new IlimapSourceRange(docStart, rbracePos);
        return new IlimapDocument(
                formatVersion, name, job, inputs, outputs, oid, basket, enums, defaults, rules, range);
    }

    private IlimapFormatVersion parseFormatVersion() {
        IlimapToken token = next();
        if (token.isKeyword("v2")) {
            return IlimapFormatVersion.V2;
        }
        throw parseError("expected format version 'v2' but got '" + token.text() + "'", token);
    }

    private String parseOptionalString() {
        IlimapToken token = peek();
        if (token.type() == IlimapTokenType.STRING) {
            return next().text();
        }
        return null;
    }

    private IlimapJobBlock parseJobBlock() {
        IlimapToken jobKeyword = expectKeyword("job");
        expectToken(IlimapTokenType.LBRACE);

        String name = null;
        String description = null;
        String direction = null;
        String failPolicy = null;
        String compileMode = null;
        List<String> modeldirs = new ArrayList<>();

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken fieldToken = next();
            if (!isKeywordOrIdentifier(fieldToken)) {
                throw parseError("expected field name", fieldToken);
            }
            String field = fieldToken.text();
            switch (field) {
                case "name" -> name = expectStringOrIdentifier();
                case "description" -> description = expectString();
                case "direction" -> direction = expectStringOrIdentifier();
                case "failPolicy" -> failPolicy = expectStringOrIdentifier();
                case "compileMode" -> compileMode = expectStringOrIdentifier();
                case "modeldir" -> modeldirs.add(expectString());
                default -> throw parseError("unexpected job field '" + field + "'", fieldToken);
            }
            expectToken(IlimapTokenType.SEMICOLON);
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(jobKeyword.range().start(), end);
        return new IlimapJobBlock(name, description, direction, failPolicy, compileMode, modeldirs, range);
    }

    private IlimapInputBlock parseInputBlock() {
        IlimapToken inputKeyword = expectKeyword("input");
        String id = expectIdentifier();
        expectToken(IlimapTokenType.LBRACE);

        String path = null;
        String model = null;
        String format = null;

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken fieldToken = next();
            if (!isKeywordOrIdentifier(fieldToken)) {
                throw parseError("expected field name", fieldToken);
            }
            String field = fieldToken.text();
            switch (field) {
                case "path" -> path = expectString();
                case "model" -> model = expectString();
                case "format" -> format = expectStringOrIdentifier();
                default -> throw parseError(
                        "unexpected field '" + field + "' in input block", fieldToken);
            }
            expectToken(IlimapTokenType.SEMICOLON);
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(inputKeyword.range().start(), end);
        return new IlimapInputBlock(id, path, model, format, range);
    }

    private IlimapOutputBlock parseOutputBlock() {
        IlimapToken outputKeyword = expectKeyword("output");
        String id = expectIdentifier();
        expectToken(IlimapTokenType.LBRACE);

        String path = null;
        String model = null;
        String format = null;

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken fieldToken = next();
            if (!isKeywordOrIdentifier(fieldToken)) {
                throw parseError("expected field name", fieldToken);
            }
            String field = fieldToken.text();
            switch (field) {
                case "path" -> path = expectString();
                case "model" -> model = expectString();
                case "format" -> format = expectStringOrIdentifier();
                default -> throw parseError(
                        "unexpected field '" + field + "' in output block", fieldToken);
            }
            expectToken(IlimapTokenType.SEMICOLON);
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(outputKeyword.range().start(), end);
        return new IlimapOutputBlock(id, path, model, format, range);
    }

    private IlimapOidBlock parseOidBlock() {
        IlimapToken oidKeyword = expectKeyword("oid");
        expectToken(IlimapTokenType.LBRACE);

        String strategy = null;
        String namespace = null;

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken fieldToken = next();
            if (!isKeywordOrIdentifier(fieldToken)) {
                throw parseError("expected field name", fieldToken);
            }
            String field = fieldToken.text();
            switch (field) {
                case "strategy" -> strategy = expectStringOrIdentifier();
                case "namespace" -> namespace = expectString();
                default -> throw parseError(
                        "unexpected field '" + field + "' in oid block", fieldToken);
            }
            expectToken(IlimapTokenType.SEMICOLON);
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(oidKeyword.range().start(), end);
        return new IlimapOidBlock(strategy, namespace, range);
    }

    private IlimapBasketStmt parseBasketStmt() {
        IlimapToken basketKeyword = expectKeyword("basket");
        String strategy = expectStringOrIdentifier();
        IlimapToken semicolon = expectToken(IlimapTokenType.SEMICOLON);
        IlimapSourceRange range = new IlimapSourceRange(basketKeyword.range().start(), semicolon.range().end());
        return new IlimapBasketStmt(strategy, range);
    }

    private IlimapEnumBlock parseEnumBlock() {
        IlimapToken enumKeyword = expectKeyword("enum");
        String id = expectIdentifier();
        expectToken(IlimapTokenType.LBRACE);

        List<IlimapEnumEntry> entries = new ArrayList<>();
        while (!peekType(IlimapTokenType.RBRACE)) {
            entries.add(parseEnumEntry());
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(enumKeyword.range().start(), end);
        return new IlimapEnumBlock(id, entries, range);
    }

    private IlimapEnumEntry parseEnumEntry() {
        IlimapSourcePosition start = currentToken().range().start();
        IlimapLiteral source = parseLiteral();
        expectToken(IlimapTokenType.ARROW);
        IlimapLiteral target = parseLiteral();
        IlimapSourcePosition semicolonEnd = expectToken(IlimapTokenType.SEMICOLON).range().end();
        IlimapSourceRange range = new IlimapSourceRange(start, semicolonEnd);
        return new IlimapEnumEntry(source, target, range);
    }

    private IlimapLiteral parseLiteral() {
        IlimapToken token = next();
        return switch (token.type()) {
            case STRING -> new IlimapLiteral.StringLit(token.text(), token.range());
            case NUMBER -> new IlimapLiteral.NumberLit(token.text(), token.range());
            case BOOLEAN -> new IlimapLiteral.BooleanLit(
                    Boolean.parseBoolean(token.text()), token.range());
            case NULL -> new IlimapLiteral.NullLit(token.range());
            case HASH_LITERAL -> new IlimapLiteral.HashLit(token.text(), token.range());
            default -> throw parseError("expected literal value", token);
        };
    }

    private IlimapDefaultsBlock parseTopLevelDefaults() {
        return parseDefaultsBlockContent(expectKeyword("defaults"));
    }

    private IlimapDefaultsBlock parseDefaultsBlockContent(IlimapToken defaultsKeyword) {
        expectToken(IlimapTokenType.LBRACE);
        List<IlimapAssignment> assignments = new ArrayList<>();
        while (!peekType(IlimapTokenType.RBRACE)) {
            assignments.add(parseAssignment());
        }
        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(defaultsKeyword.range().start(), end);
        return new IlimapDefaultsBlock(assignments, range);
    }

    private IlimapRuleBlock parseRuleBlock() {
        IlimapToken ruleKeyword = expectKeyword("rule");
        String id = expectIdentifier();
        expectToken(IlimapTokenType.LBRACE);

        List<IlimapRuleElement> elements = new ArrayList<>();
        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken token = peek();
            if (token.type() == IlimapTokenType.KEYWORD) {
                elements.add(switch (token.text()) {
                    case "target" -> {
                        advance();
                        yield parseTargetStmt();
                    }
                    case "source" -> {
                        advance();
                        yield parseSourceStmt();
                    }
                    case "where" -> {
                        advance();
                        yield parseWhereStmt();
                    }
                    case "identity" -> {
                        advance();
                        yield parseIdentityStmt();
                    }
                    case "assign" -> {
                        advance();
                        yield parseRuleAssign();
                    }
                    case "defaults" -> {
                        advance();
                        yield parseRuleDefaults();
                    }
                    default -> throw parseError(
                            "unexpected keyword '" + token.text() + "' inside rule", token);
                });
            } else {
                throw parseError(
                        "unexpected token '" + token.text() + "' inside rule", token);
            }
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(ruleKeyword.range().start(), end);
        return new IlimapRuleBlock(id, elements, range);
    }

    private IlimapTargetStmt parseTargetStmt() {
        IlimapToken targetKeyword = lastConsumed();
        String outputId = expectIdentifier();
        expectKeyword("class");
        String targetClass = expectString();
        IlimapSourcePosition end = expectToken(IlimapTokenType.SEMICOLON).range().end();
        IlimapSourceRange range = new IlimapSourceRange(targetKeyword.range().start(), end);
        return new IlimapTargetStmt(outputId, targetClass, range);
    }

    private IlimapSourceStmt parseSourceStmt() {
        IlimapToken sourceKeyword = lastConsumed();
        String alias = expectIdentifier();
        expectKeyword("from");
        List<String> inputIds = new ArrayList<>();
        inputIds.add(expectIdentifier());
        while (peekType(IlimapTokenType.COMMA)) {
            advance();
            inputIds.add(expectIdentifier());
        }
        expectKeyword("class");
        String sourceClass = expectString();
        IlimapExpressionText where = null;
        if (peekKeyword("where")) {
            advance();
            where = readExpression();
        }
        IlimapSourcePosition end;
        if (where != null) {
            end = where.range().end();
        } else {
            end = expectToken(IlimapTokenType.SEMICOLON).range().end();
        }
        IlimapSourceRange range = new IlimapSourceRange(sourceKeyword.range().start(), end);
        return new IlimapSourceStmt(alias, inputIds, sourceClass, where, range);
    }

    private IlimapWhereStmt parseWhereStmt() {
        IlimapToken whereKeyword = lastConsumed();
        IlimapExpressionText expression = readExpression();
        IlimapSourceRange range = new IlimapSourceRange(whereKeyword.range().start(), expression.range().end());
        return new IlimapWhereStmt(expression, range);
    }

    private IlimapIdentityStmt parseIdentityStmt() {
        IlimapToken identityKeyword = lastConsumed();
        IlimapExpressionText blob = readExpression();
        List<IlimapExpressionText> expressions = new ArrayList<>();
        String text = blob.text();
        if (!text.isEmpty()) {
            String[] parts = text.split(",", -1);
            for (String part : parts) {
                expressions.add(new IlimapExpressionText(part.strip(), blob.range()));
            }
        }
        IlimapSourceRange range = new IlimapSourceRange(identityKeyword.range().start(), blob.range().end());
        return new IlimapIdentityStmt(expressions, range);
    }

    private IlimapAssignmentBlock parseRuleAssign() {
        IlimapToken assignKeyword = lastConsumed();
        expectToken(IlimapTokenType.LBRACE);
        List<IlimapAssignment> assignments = new ArrayList<>();
        while (!peekType(IlimapTokenType.RBRACE)) {
            assignments.add(parseAssignment());
        }
        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(assignKeyword.range().start(), end);
        return new IlimapAssignmentBlock(assignments, range);
    }

    private IlimapDefaultsBlock parseRuleDefaults() {
        IlimapToken defaultsKeyword = lastConsumed();
        return parseDefaultsBlockContent(defaultsKeyword);
    }

    private IlimapAssignment parseAssignment() {
        IlimapSourcePosition start = currentToken().range().start();
        String attribute = expectIdentifier();
        expectToken(IlimapTokenType.EQUALS);
        IlimapExpressionText expression = readExpression();
        IlimapSourceRange range = new IlimapSourceRange(start, expression.range().end());
        return new IlimapAssignment(attribute, expression, range);
    }

    private IlimapExpressionText readExpression() {
        IlimapToken token = peek();
        int startOffset = token.range().start().offset();
        IlimapExpressionText result =
                expressionReader.readUntilStatementSemicolon(source, startOffset);
        int afterSemicolon = result.range().end().offset() + 1;
        lexer.skipTo(afterSemicolon);
        return result;
    }

    private IlimapToken currentToken() {
        return lexer.peek();
    }

    private IlimapToken next() {
        if (!lexer.hasNext()) {
            IlimapToken eofToken = lexer.peek();
            if (eofToken.type() == IlimapTokenType.EOF) {
                IlimapToken token = lexer.next();
                lastConsumed = token;
                return token;
            }
            throw parseError("unexpected end of input", eofToken);
        }
        IlimapToken token = lexer.next();
        lastConsumed = token;
        return token;
    }

    private IlimapToken peek() {
        return lexer.peek();
    }

    private boolean peekKeyword(String keyword) {
        IlimapToken token = lexer.peek();
        return token.type() == IlimapTokenType.KEYWORD && token.text().equals(keyword);
    }

    private boolean peekType(IlimapTokenType type) {
        return lexer.peek().type() == type;
    }

    private void advance() {
        IlimapToken token = lexer.next();
        lastConsumed = token;
    }

    private IlimapToken lastConsumed() {
        if (lastConsumed == null) {
            throw new IllegalStateException("no token has been consumed yet");
        }
        return lastConsumed;
    }

    private boolean isKeywordOrIdentifier(IlimapToken token) {
        return token.type() == IlimapTokenType.KEYWORD || token.type() == IlimapTokenType.IDENTIFIER;
    }

    private IlimapToken expectKeyword(String keyword) {
        IlimapToken token = next();
        if (!token.isKeyword(keyword)) {
            throw parseError("expected keyword '" + keyword + "' but got '" + token.text() + "'", token);
        }
        return token;
    }

    private IlimapToken expectToken(IlimapTokenType type) {
        IlimapToken token = next();
        if (token.type() != type) {
            throw parseError(
                    "expected '" + type + "' but got '" + token.text() + "' (" + token.type() + ")",
                    token);
        }
        return token;
    }

    private String expectIdentifier() {
        IlimapToken token = next();
        if (token.type() != IlimapTokenType.IDENTIFIER) {
            throw parseError(
                    "expected identifier but got '" + token.text() + "' (" + token.type() + ")",
                    token);
        }
        return token.text();
    }

    private String expectString() {
        IlimapToken token = next();
        if (token.type() != IlimapTokenType.STRING) {
            throw parseError("expected string but got '" + token.text() + "'", token);
        }
        return token.text();
    }

    private String expectStringOrIdentifier() {
        IlimapToken token = next();
        if (token.type() != IlimapTokenType.STRING && token.type() != IlimapTokenType.IDENTIFIER) {
            throw parseError("expected string or identifier but got '" + token.text() + "'", token);
        }
        return token.text();
    }

    private ParseException parseError(String message, IlimapToken token) {
        IlimapSourcePosition pos = token.range().start();
        return new ParseException(
                "at line " + pos.line() + ", column " + pos.column() + ": " + message, pos);
    }

    public static final class ParseException extends RuntimeException {
        public final IlimapSourcePosition position;

        ParseException(String message, IlimapSourcePosition position) {
            super(message);
            this.position = position;
        }
    }
}
