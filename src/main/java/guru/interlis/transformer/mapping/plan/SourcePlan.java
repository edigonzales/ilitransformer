package guru.interlis.transformer.mapping.plan;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Table;

public record SourcePlan(
        String alias,
        Table sourceClass,
        java.util.List<String> inputIds,
        String where
) {}
