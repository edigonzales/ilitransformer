package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.expr.Expression;

import java.util.Set;

public record CompiledExpression(
        String sourceText,
        Expression ast,
        TypeInfo resultType,
        boolean deterministic,
        Set<ResolvedPath> referencedPaths) {}
