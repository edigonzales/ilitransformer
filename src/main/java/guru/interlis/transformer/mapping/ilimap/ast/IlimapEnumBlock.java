package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

public record IlimapEnumBlock(String id, List<IlimapEnumEntry> entries, IlimapSourceRange range)
        implements IlimapAstNode {}
