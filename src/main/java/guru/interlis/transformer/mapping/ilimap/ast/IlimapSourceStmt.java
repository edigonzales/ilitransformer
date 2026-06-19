package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

public record IlimapSourceStmt(
        String alias, List<String> inputIds, String sourceClass, IlimapExpressionText where, IlimapSourceRange range)
        implements IlimapRuleElement {}
