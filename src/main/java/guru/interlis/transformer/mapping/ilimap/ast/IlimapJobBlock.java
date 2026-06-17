package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;
import java.util.List;

public record IlimapJobBlock(
        String name,
        String description,
        String direction,
        String failPolicy,
        String compileMode,
        List<String> modeldirs,
        IlimapSourceRange range)
        implements IlimapAstNode {}
