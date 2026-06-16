package guru.interlis.transformer.mapping.plan;

public record ResolvedPath(String alias, String attributeOrRole, String sourceClassName, TypeInfo type) {}
