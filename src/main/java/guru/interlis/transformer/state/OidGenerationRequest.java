package guru.interlis.transformer.state;

import java.util.LinkedHashMap;

public record OidGenerationRequest(
        OidStrategy strategy,
        String namespace,
        String ruleId,
        String inputId,
        String sourceBasketId,
        String sourceClass,
        String sourceOid,
        LinkedHashMap<String, CanonicalValue> identityValues,
        String targetOidType) {}
