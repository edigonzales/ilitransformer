package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

public record IlimapAssignmentBlock(List<IlimapAssignment> assignments, IlimapSourceRange range)
        implements IlimapRuleElement {}
