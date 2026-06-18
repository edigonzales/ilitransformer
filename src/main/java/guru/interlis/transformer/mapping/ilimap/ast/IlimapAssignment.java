package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapAssignment(String targetAttribute, IlimapExpressionText expression, IlimapSourceRange range)
        implements IlimapAstNode {}
