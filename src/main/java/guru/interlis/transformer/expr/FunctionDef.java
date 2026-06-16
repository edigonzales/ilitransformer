package guru.interlis.transformer.expr;

import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;

public record FunctionDef(
        String name,
        List<FunctionParam> parameters,
        boolean variadic,
        TypeResolver returnTypeResolver,
        boolean deterministic,
        EvaluationMode evaluationMode,
        FunctionImplementation implementation,
        LazyFunctionImplementation lazyImplementation) {

    public record FunctionParam(String name, TypeInfo type) {}

    public TypeInfo returnType() {
        return returnTypeResolver != null ? returnTypeResolver.resolveReturnType(List.of()) : TypeInfo.UNKNOWN;
    }

    public static FunctionDef eager(
            String name, TypeInfo returnType, List<FunctionParam> params, FunctionImplementation impl) {
        return new FunctionDef(name, params, false, argTypes -> returnType, true, EvaluationMode.EAGER, impl, null);
    }

    public static FunctionDef eagerNonDeterministic(
            String name, TypeInfo returnType, List<FunctionParam> params, FunctionImplementation impl) {
        return new FunctionDef(name, params, false, argTypes -> returnType, false, EvaluationMode.EAGER, impl, null);
    }

    public static FunctionDef lazy(
            String name,
            TypeResolver returnTypeResolver,
            List<FunctionParam> params,
            boolean deterministic,
            LazyFunctionImplementation impl) {
        return new FunctionDef(name, params, false, returnTypeResolver, deterministic, EvaluationMode.LAZY, null, impl);
    }

    public static FunctionDef lazyVariadic(
            String name,
            TypeResolver returnTypeResolver,
            List<FunctionParam> params,
            boolean deterministic,
            LazyFunctionImplementation impl) {
        return new FunctionDef(name, params, true, returnTypeResolver, deterministic, EvaluationMode.LAZY, null, impl);
    }
}
