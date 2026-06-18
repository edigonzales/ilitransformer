package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapParentRefStmt(String kind, String name, String parentAlias, IlimapSourceRange range)
        implements IlimapBagElement {}
