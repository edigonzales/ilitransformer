package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapWhereStmt(IlimapExpressionText expression, IlimapSourceRange range) implements IlimapRuleElement {}
