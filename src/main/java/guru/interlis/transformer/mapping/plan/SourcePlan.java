package guru.interlis.transformer.mapping.plan;

import ch.interlis.ili2c.metamodel.AbstractClassDef;

import java.util.List;

public record SourcePlan(
        String alias,
        AbstractClassDef<?> sourceClass,
        List<String> inputIds,
        CompiledExpression where,
        List<String> structurePath) {
    public SourcePlan {
        structurePath = structurePath == null ? List.of() : List.copyOf(structurePath);
    }

    public SourcePlan(String alias, AbstractClassDef<?> sourceClass, List<String> inputIds, CompiledExpression where) {
        this(alias, sourceClass, inputIds, where, List.of());
    }
}
