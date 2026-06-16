package guru.interlis.transformer.mapping.plan;

public record LossPlan(String sourcePath, String reasonCode, String description, CompiledExpression whenExpression) {}
