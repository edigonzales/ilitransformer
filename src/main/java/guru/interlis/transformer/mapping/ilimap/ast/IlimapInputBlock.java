package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapInputBlock(String id, String path, String model, String format, IlimapSourceRange range)
        implements IlimapAstNode {}
