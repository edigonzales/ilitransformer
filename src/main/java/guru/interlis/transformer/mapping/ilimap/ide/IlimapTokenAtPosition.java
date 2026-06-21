package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAstNode;

public record IlimapTokenAtPosition(String text, IlimapIdeRange range, IlimapAstNode surroundingNode) {}
