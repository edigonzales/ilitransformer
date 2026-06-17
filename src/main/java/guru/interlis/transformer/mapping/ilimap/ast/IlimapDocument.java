package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;
import java.util.List;

public record IlimapDocument(
        IlimapFormatVersion formatVersion,
        String name,
        IlimapJobBlock job,
        List<IlimapInputBlock> inputs,
        List<IlimapOutputBlock> outputs,
        IlimapOidBlock oid,
        IlimapBasketStmt basket,
        List<IlimapEnumBlock> enums,
        IlimapDefaultsBlock defaults,
        List<IlimapRuleBlock> rules,
        IlimapSourceRange range)
        implements IlimapAstNode {}
