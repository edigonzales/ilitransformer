package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapRefBlock(
        String id,
        String association,
        String role,
        boolean required,
        String targetRuleId,
        IlimapExpressionText sourceRef,
        IlimapSourceRange range)
        implements IlimapRuleElement {}
