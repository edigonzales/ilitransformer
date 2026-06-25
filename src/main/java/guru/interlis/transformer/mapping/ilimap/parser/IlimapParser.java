package guru.interlis.transformer.mapping.ilimap.parser;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IlimapParser {

    private static final String JOB_FIELDS = "name, description, direction, failPolicy, compileMode, modeldir";
    private static final String TRANSFER_FIELDS = "path, model, format, option";
    private static final String OID_FIELDS = "strategy, namespace";

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

        IlimapOidBlock oid = peekKeyword("oid") ? parseOidBlock() : null;
        IlimapBasketStmt basket = peekKeyword("basket") ? parseBasketStmt() : null;

        List<IlimapEnumBlock> enums = new ArrayList<>();
        while (peekKeyword("enum")) {
            enums.add(parseEnumBlock());
        }

        IlimapDefaultsBlock defaults = peekKeyword("defaults") ? parseTopLevelDefaults() : null;

        List<IlimapRuleBlock> rules = new ArrayList<>();
        while (peekKeyword("rule")) {
            rules.add(parseRuleBlock());
        }

        IlimapSourcePosition rbracePos =
                expectToken(IlimapTokenType.RBRACE).range().end();
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
                default ->
                    throw parseError("unexpected job field '" + field + "'. Allowed fields: " + JOB_FIELDS, fieldToken);
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
        Map<String, String> options = new LinkedHashMap<>();

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
                case "option" -> parseOption(options);
                default ->
                    throw parseError(
                            "unexpected field '" + field + "' in input block. Allowed fields: " + TRANSFER_FIELDS,
                            fieldToken);
            }
            expectToken(IlimapTokenType.SEMICOLON);
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(inputKeyword.range().start(), end);
        return new IlimapInputBlock(id, path, model, format, options, range);
    }

    private IlimapOutputBlock parseOutputBlock() {
        IlimapToken outputKeyword = expectKeyword("output");
        String id = expectIdentifier();
        expectToken(IlimapTokenType.LBRACE);

        String path = null;
        String model = null;
        String format = null;
        Map<String, String> options = new LinkedHashMap<>();

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
                case "option" -> parseOption(options);
                default ->
                    throw parseError(
                            "unexpected field '" + field + "' in output block. Allowed fields: " + TRANSFER_FIELDS,
                            fieldToken);
            }
            expectToken(IlimapTokenType.SEMICOLON);
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(outputKeyword.range().start(), end);
        return new IlimapOutputBlock(id, path, model, format, options, range);
    }

    private void parseOption(Map<String, String> options) {
        String key = expectStringOrIdentifier();
        String value = parseOptionValue();
        options.put(key, value);
    }

    private String parseOptionValue() {
        IlimapToken token = next();
        return switch (token.type()) {
            case STRING, NUMBER, BOOLEAN -> token.text();
            default ->
                throw parseError(
                        "expected string, number or boolean option value but got '" + token.text() + "'", token);
        };
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
                default ->
                    throw parseError(
                            "unexpected field '" + field + "' in oid block. Allowed fields: " + OID_FIELDS, fieldToken);
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
        IlimapSourceRange range = new IlimapSourceRange(
                basketKeyword.range().start(), semicolon.range().end());
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
        IlimapSourcePosition semicolonEnd =
                expectToken(IlimapTokenType.SEMICOLON).range().end();
        IlimapSourceRange range = new IlimapSourceRange(start, semicolonEnd);
        return new IlimapEnumEntry(source, target, range);
    }

    private IlimapLiteral parseLiteral() {
        IlimapToken token = next();
        return switch (token.type()) {
            case STRING -> new IlimapLiteral.StringLit(token.text(), token.range());
            case NUMBER -> new IlimapLiteral.NumberLit(token.text(), token.range());
            case BOOLEAN -> new IlimapLiteral.BooleanLit(Boolean.parseBoolean(token.text()), token.range());
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
                elements.add(
                        switch (token.text()) {
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
                            case "join" -> {
                                advance();
                                yield parseJoinStmt();
                            }
                            case "bag" -> {
                                advance();
                                yield parseBagBlock();
                            }
                            case "ref" -> {
                                advance();
                                yield parseRefBlock();
                            }
                            case "create" -> {
                                advance();
                                yield parseCreateBlock();
                            }
                            case "loss" -> {
                                advance();
                                yield parseLossBlock();
                            }
                            case "metadata" -> {
                                advance();
                                yield parseMetadataBlock();
                            }
                            default -> throw parseError("unexpected keyword '" + token.text() + "' inside rule", token);
                        });
            } else {
                throw parseError("unexpected token '" + token.text() + "' inside rule", token);
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
        IlimapSourcePosition end =
                expectToken(IlimapTokenType.SEMICOLON).range().end();
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
        IlimapSourceRange range = new IlimapSourceRange(
                whereKeyword.range().start(), expression.range().end());
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
        IlimapSourceRange range = new IlimapSourceRange(
                identityKeyword.range().start(), blob.range().end());
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

    private IlimapJoinStmt parseJoinStmt() {
        IlimapToken joinKeyword = lastConsumed();
        IlimapToken typeToken = next();
        if (!typeToken.isKeyword("inner") && !typeToken.isKeyword("left")) {
            throw parseError("expected 'inner' or 'left' after 'join' but got '" + typeToken.text() + "'", typeToken);
        }
        String joinType = typeToken.text();
        String leftAlias = expectIdentifier();
        expectKeyword("to");
        String rightAlias = expectIdentifier();
        expectKeyword("on");
        IlimapExpressionText on = readExpression();
        IlimapSourceRange range =
                new IlimapSourceRange(joinKeyword.range().start(), on.range().end());
        return new IlimapJoinStmt(joinType, leftAlias, rightAlias, on, range);
    }

    private IlimapBagBlock parseBagBlock() {
        IlimapToken bagKeyword = lastConsumed();
        String id = expectIdentifier();
        expectToken(IlimapTokenType.LBRACE);

        String targetAttribute = null;
        IlimapBagFromStmt from = null;
        String structure = null;
        String mode = null;
        Integer maxItems = null;
        IlimapExpressionText where = null;
        IlimapParentRefStmt parentRef = null;
        IlimapAssignmentBlock assign = null;
        List<IlimapBagBlock> nestedBags = new ArrayList<>();

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken token = peek();
            if (token.type() == IlimapTokenType.KEYWORD) {
                switch (token.text()) {
                    case "target" -> {
                        advance();
                        targetAttribute = expectStringOrIdentifier();
                        expectToken(IlimapTokenType.SEMICOLON);
                    }
                    case "from" -> {
                        advance();
                        from = parseBagFromStmt();
                    }
                    case "structure" -> {
                        advance();
                        structure = expectString();
                        expectToken(IlimapTokenType.SEMICOLON);
                    }
                    case "mode" -> {
                        advance();
                        IlimapToken modeToken = next();
                        if (modeToken.isKeyword("embed") || modeToken.isKeyword("expand")) {
                            mode = modeToken.text();
                        } else {
                            throw parseError(
                                    "expected 'embed' or 'expand' after 'mode' but got '" + modeToken.text() + "'",
                                    modeToken);
                        }
                        expectToken(IlimapTokenType.SEMICOLON);
                    }
                    case "maxItems" -> {
                        advance();
                        IlimapToken numberToken = expectToken(IlimapTokenType.NUMBER);
                        try {
                            maxItems = Integer.parseInt(numberToken.text());
                        } catch (NumberFormatException e) {
                            throw parseError(
                                    "maxItems must be an integer, got '" + numberToken.text() + "'", numberToken);
                        }
                        expectToken(IlimapTokenType.SEMICOLON);
                    }
                    case "where" -> {
                        advance();
                        where = readExpression();
                    }
                    case "parentRef" -> {
                        advance();
                        parentRef = parseParentRefStmt();
                    }
                    case "assign" -> {
                        advance();
                        assign = parseRuleAssign();
                    }
                    case "bag" -> {
                        advance();
                        nestedBags.add(parseBagBlock());
                    }
                    default -> throw parseError("unexpected keyword '" + token.text() + "' inside bag", token);
                }
            } else {
                throw parseError("unexpected token '" + token.text() + "' inside bag", token);
            }
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(bagKeyword.range().start(), end);
        return new IlimapBagBlock(
                id, targetAttribute, from, structure, mode, maxItems, where, parentRef, assign, nestedBags, range);
    }

    private IlimapBagFromStmt parseBagFromStmt() {
        IlimapToken fromKeyword = lastConsumed();
        String alias = expectIdentifier();
        expectKeyword("in");
        String inputId = expectIdentifier();
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
        IlimapSourceRange range = new IlimapSourceRange(fromKeyword.range().start(), end);
        return new IlimapBagFromStmt(alias, inputId, sourceClass, where, range);
    }

    private IlimapParentRefStmt parseParentRefStmt() {
        IlimapToken parentRefKeyword = lastConsumed();
        IlimapToken kindToken = next();
        if (!kindToken.isKeyword("attribute") && !kindToken.isKeyword("role")) {
            throw parseError(
                    "expected 'attribute' or 'role' after 'parentRef' but got '" + kindToken.text() + "'", kindToken);
        }
        String kind = kindToken.text();
        String name = expectString();
        expectKeyword("parent");
        String parentAlias = expectIdentifier();
        IlimapSourcePosition end =
                expectToken(IlimapTokenType.SEMICOLON).range().end();
        IlimapSourceRange range = new IlimapSourceRange(parentRefKeyword.range().start(), end);
        return new IlimapParentRefStmt(kind, name, parentAlias, range);
    }

    private IlimapRefBlock parseRefBlock() {
        IlimapToken refKeyword = lastConsumed();
        String id = expectIdentifier();

        IlimapToken nextToken = peek();
        if (nextToken.type() == IlimapTokenType.ARROW && nextToken.text().equals("->")) {
            throw parseError(
                    "ref short form '->' is not supported in v2.0; use the long form with 'target rule'", nextToken);
        }

        expectToken(IlimapTokenType.LBRACE);

        String association = null;
        String role = null;
        boolean required = false;
        String targetRuleId = null;
        IlimapExpressionText sourceRef = null;

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken token = peek();
            if (token.type() == IlimapTokenType.KEYWORD) {
                switch (token.text()) {
                    case "association" -> {
                        advance();
                        association = expectString();
                        expectToken(IlimapTokenType.SEMICOLON);
                    }
                    case "role" -> {
                        advance();
                        role = expectString();
                        expectToken(IlimapTokenType.SEMICOLON);
                    }
                    case "required" -> {
                        advance();
                        required = true;
                        expectToken(IlimapTokenType.SEMICOLON);
                    }
                    case "target" -> {
                        advance();
                        expectKeyword("rule");
                        targetRuleId = expectIdentifier();
                        expectKeyword("sourceRef");
                        sourceRef = readExpression();
                    }
                    default -> throw parseError("unexpected keyword '" + token.text() + "' inside ref", token);
                }
            } else {
                throw parseError("unexpected token '" + token.text() + "' inside ref", token);
            }
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(refKeyword.range().start(), end);
        return new IlimapRefBlock(id, association, role, required, targetRuleId, sourceRef, range);
    }

    private IlimapCreateBlock parseCreateBlock() {
        IlimapToken createKeyword = lastConsumed();
        expectKeyword("class");
        String targetClass = expectString();
        expectToken(IlimapTokenType.LBRACE);

        IlimapAssignmentBlock assign = null;
        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken token = peek();
            if (token.type() == IlimapTokenType.KEYWORD && token.text().equals("assign")) {
                advance();
                assign = parseRuleAssign();
            } else {
                throw parseError("unexpected token '" + token.text() + "' inside create", token);
            }
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(createKeyword.range().start(), end);
        return new IlimapCreateBlock(targetClass, assign, range);
    }

    private IlimapLossBlock parseLossBlock() {
        IlimapToken lossKeyword = lastConsumed();
        expectToken(IlimapTokenType.LBRACE);

        IlimapExpressionText sourcePath = null;
        String reasonCode = null;
        String description = null;
        IlimapExpressionText when = null;

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken fieldToken = next();
            if (!isKeywordOrIdentifier(fieldToken)) {
                throw parseError("expected field name", fieldToken);
            }
            switch (fieldToken.text()) {
                case "sourcePath" -> sourcePath = readExpression();
                case "reasonCode" -> {
                    reasonCode = expectString();
                    expectToken(IlimapTokenType.SEMICOLON);
                }
                case "description" -> {
                    description = expectString();
                    expectToken(IlimapTokenType.SEMICOLON);
                }
                case "when" -> when = readExpression();
                default -> throw parseError("unexpected field '" + fieldToken.text() + "' in loss block", fieldToken);
            }
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(lossKeyword.range().start(), end);
        return new IlimapLossBlock(sourcePath, reasonCode, description, when, range);
    }

    private IlimapMetadataBlock parseMetadataBlock() {
        IlimapToken metadataKeyword = lastConsumed();
        expectToken(IlimapTokenType.LBRACE);

        String direction = null;
        String roundtrip = null;
        String lossiness = null;

        while (!peekType(IlimapTokenType.RBRACE)) {
            IlimapToken fieldToken = next();
            if (!isKeywordOrIdentifier(fieldToken)) {
                throw parseError("expected field name", fieldToken);
            }
            switch (fieldToken.text()) {
                case "direction" -> {
                    direction = expectStringOrIdentifier();
                    expectToken(IlimapTokenType.SEMICOLON);
                }
                case "roundtrip" -> {
                    roundtrip = expectStringOrIdentifier();
                    expectToken(IlimapTokenType.SEMICOLON);
                }
                case "lossiness" -> {
                    lossiness = expectStringOrIdentifier();
                    expectToken(IlimapTokenType.SEMICOLON);
                }
                default ->
                    throw parseError("unexpected field '" + fieldToken.text() + "' in metadata block", fieldToken);
            }
        }

        IlimapSourcePosition end = expectToken(IlimapTokenType.RBRACE).range().end();
        IlimapSourceRange range = new IlimapSourceRange(metadataKeyword.range().start(), end);
        return new IlimapMetadataBlock(direction, roundtrip, lossiness, range);
    }

    private IlimapAssignment parseAssignment() {
        IlimapSourcePosition start = currentToken().range().start();
        String attribute = expectIdentifier();
        expectToken(IlimapTokenType.EQUALS);
        IlimapExpressionText expression = readExpression();
        IlimapSourceRange range =
                new IlimapSourceRange(start, expression.range().end());
        return new IlimapAssignment(attribute, expression, range);
    }

    private IlimapExpressionText readExpression() {
        IlimapToken token = peek();
        int startOffset = token.range().start().offset();
        IlimapExpressionText result = expressionReader.readUntilStatementSemicolon(source, startOffset);
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
            throw parseError("expected '" + type + "' but got '" + token.text() + "' (" + token.type() + ")", token);
        }
        return token;
    }

    private String expectIdentifier() {
        IlimapToken token = next();
        if (token.type() != IlimapTokenType.IDENTIFIER) {
            throw parseError("expected identifier but got '" + token.text() + "' (" + token.type() + ")", token);
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
        return new ParseException("at line " + pos.line() + ", column " + pos.column() + ": " + message, pos);
    }

    public static final class ParseException extends RuntimeException {
        public final IlimapSourcePosition position;

        ParseException(String message, IlimapSourcePosition position) {
            super(message);
            this.position = position;
        }
    }
}
