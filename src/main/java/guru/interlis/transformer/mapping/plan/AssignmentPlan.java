package guru.interlis.transformer.mapping.plan;

public record AssignmentPlan(
        String targetAttrName, ch.interlis.ili2c.metamodel.AttributeDef targetAttr, CompiledExpression expression) {}
