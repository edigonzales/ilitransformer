package guru.interlis.transformer.mapping.ilimap.ast;

public sealed interface IlimapBagElement extends IlimapAstNode
        permits IlimapBagBlock, IlimapParentRefStmt {}
