package guru.interlis.transformer.mapping.plan;

import ch.interlis.ili2c.metamodel.Table;

public record SourcePlan(
        String alias,
        ch.interlis.ili2c.metamodel.Table sourceClass,
        java.util.List<String> inputIds,
        CompiledExpression where
) {}
