package guru.interlis.transformer.mapping.plan;

public record RefPlan(
        String targetRoleName, String association, String sourceRef, String targetRuleId, boolean required) {}
