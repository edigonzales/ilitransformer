package guru.interlis.transformer.mapping.ilimap.semantic;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAstNode;

public record IlimapSymbol(IlimapSymbolKind kind, String name, IlimapAstNode node) {}
