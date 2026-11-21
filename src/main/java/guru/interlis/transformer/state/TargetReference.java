package guru.interlis.transformer.state;

public record TargetReference(
        String outputId, String targetClass, String targetOid, String targetBasketId, String producingRuleId) {}
