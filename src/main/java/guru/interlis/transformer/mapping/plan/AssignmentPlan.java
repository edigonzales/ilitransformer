package guru.interlis.transformer.mapping.plan;

import ch.interlis.ili2c.metamodel.AttributeDef;

public record AssignmentPlan(
        String targetAttrName,
        AttributeDef targetAttr,
        String expression,
        ExpressionKind exprKind,
        TypeInfo expectedType
) {}
