package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapOidBlock(String strategy, String namespace, IlimapSourceRange range) implements IlimapAstNode {}
