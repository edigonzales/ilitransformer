package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapJoinStmt(
        String joinType,
        String leftAlias,
        String rightAlias,
        IlimapExpressionText on,
        IlimapSourceRange range) implements IlimapRuleElement {}
