package guru.interlis.transformer.mapping.plan;

public record JoinPlan(
        String id,
        JoinType type,
        SourcePlan left,
        SourcePlan right,
        CompiledExpression condition,
        JoinCardinality expectedCardinality) {}
