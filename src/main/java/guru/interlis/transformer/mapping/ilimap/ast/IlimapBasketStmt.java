package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapBasketStmt(String strategy, IlimapSourceRange range)
        implements IlimapAstNode {}
