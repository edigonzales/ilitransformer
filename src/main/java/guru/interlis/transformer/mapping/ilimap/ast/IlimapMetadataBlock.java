package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapMetadataBlock(String direction, String roundtrip, String lossiness, IlimapSourceRange range)
        implements IlimapRuleElement {}
