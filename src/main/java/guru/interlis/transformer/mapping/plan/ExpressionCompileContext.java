package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.expr.FunctionRegistry;

import java.util.Map;

public record ExpressionCompileContext(
        String ruleId,
        Map<String, SourcePlan> sourcesByAlias,
        TypeInfo expectedTargetType,
        FunctionRegistry functionRegistry,
        Map<String, Map<String, String>> enumMaps) {}
