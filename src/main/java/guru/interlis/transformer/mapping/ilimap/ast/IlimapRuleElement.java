package guru.interlis.transformer.mapping.ilimap.ast;

public sealed interface IlimapRuleElement extends IlimapAstNode
        permits IlimapTargetStmt,
                IlimapSourceStmt,
                IlimapWhereStmt,
                IlimapIdentityStmt,
                IlimapAssignmentBlock,
                IlimapDefaultsBlock,
                IlimapJoinStmt,
                IlimapBagBlock,
                IlimapRefBlock,
                IlimapCreateBlock,
                IlimapLossBlock,
                IlimapMetadataBlock {}
