package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

public record IlimapIdentityStmt(List<IlimapExpressionText> expressions, IlimapSourceRange range)
        implements IlimapRuleElement {}
