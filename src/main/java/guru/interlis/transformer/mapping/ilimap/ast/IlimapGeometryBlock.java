package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

/** JDBC {@code geometry { ... }} block inside a query block. Maps a result-set column to a source model geometry attribute. */
public record IlimapGeometryBlock(
        String attribute, String column, String encoding, String type, Integer srid, IlimapSourceRange range)
        implements IlimapAstNode {}
