package guru.interlis.transformer.engine;

import guru.interlis.transformer.state.BasketStrategy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class BasketRouter {

    private BasketRouter() {}

    public static String determineTargetBasket(
            BasketStrategy strategy, String sourceBasketId, String targetTopic, String targetClass) {
        return switch (strategy) {
            case PRESERVE -> sourceBasketId;
            case GENERATE_UUID -> UUID.randomUUID().toString();
            case PRESERVE_OR_GENERATE_UUID -> preserveOrGenerate(sourceBasketId);
            case BY_TOPIC ->
                UUID.nameUUIDFromBytes(("basket:" + targetTopic).getBytes(StandardCharsets.UTF_8))
                        .toString();
            case EXPRESSION -> null;
        };
    }

    private static String preserveOrGenerate(String sourceBasketId) {
        if (sourceBasketId != null && isUuid(sourceBasketId)) {
            return sourceBasketId;
        }
        return UUID.randomUUID().toString();
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() < 32) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
