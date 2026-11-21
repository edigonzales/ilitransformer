package guru.interlis.transformer.engine;

import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.state.BasketStrategy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BasketRouter {

    private record BasketGroup(String outputId, String topic, String inputId, String sourceBid) {}

    private final Map<BasketGroup, String> generated = new HashMap<>();

    public String determineTargetBasket(
            BasketStrategy strategy,
            String sourceInputId,
            String sourceBasketId,
            String targetTopic,
            OutputBinding output) {
        // ITF writes a topic block rather than a basket identifier.
        if (output != null && "itf".equals(output.format())) return null;
        return switch (strategy) {
            case PRESERVE -> sourceBasketId;
            case GENERATE_UUID -> generatedBid(output, targetTopic, sourceInputId, sourceBasketId);
            case PRESERVE_OR_GENERATE_UUID ->
                isUuid(sourceBasketId)
                        ? sourceBasketId
                        : generatedBid(output, targetTopic, sourceInputId, sourceBasketId);
            case BY_TOPIC ->
                UUID.nameUUIDFromBytes(("basket:" + targetTopic).getBytes(StandardCharsets.UTF_8))
                        .toString();
            case EXPRESSION -> null;
        };
    }

    private String generatedBid(OutputBinding output, String topic, String inputId, String sourceBid) {
        var group = new BasketGroup(output == null ? null : output.outputId(), topic, inputId, sourceBid);
        return generated.computeIfAbsent(group, ignored -> UUID.randomUUID().toString());
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
