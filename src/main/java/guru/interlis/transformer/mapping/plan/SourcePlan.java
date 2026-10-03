package guru.interlis.transformer.mapping.plan;

import java.util.List;

public record SourcePlan(
        String alias,
        ch.interlis.ili2c.metamodel.Table sourceClass,
        List<String> inputIds,
        CompiledExpression where,
        List<String> structurePath) {
    public SourcePlan {
        structurePath = structurePath == null ? List.of() : List.copyOf(structurePath);
    }

    public SourcePlan(
            String alias,
            ch.interlis.ili2c.metamodel.Table sourceClass,
            List<String> inputIds,
            CompiledExpression where) {
        this(alias, sourceClass, inputIds, where, List.of());
    }
}
