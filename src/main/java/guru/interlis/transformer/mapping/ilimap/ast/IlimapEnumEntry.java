package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapEnumEntry(
        IlimapLiteral source, IlimapLiteral target, IlimapSourceRange range)
        implements IlimapAstNode {}
