package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapLossBlock(
        IlimapExpressionText sourcePath,
        String reasonCode,
        String description,
        IlimapExpressionText when,
        IlimapSourceRange range) implements IlimapRuleElement {}
