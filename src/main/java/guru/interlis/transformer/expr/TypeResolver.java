package guru.interlis.transformer.expr;

import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;

@FunctionalInterface
public interface TypeResolver {
    TypeInfo resolveReturnType(List<TypeInfo> argTypes);
}
