package guru.interlis.transformer.state;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class DefaultOidGenerationService implements OidGenerationService {

    private final AtomicLong integerSequence = new AtomicLong();
    private final AtomicLong fallbackSequence = new AtomicLong();

    @Override
    public String generate(OidGenerationRequest request) {
        return switch (request.strategy()) {
            case INTEGER -> Long.toString(integerSequence.incrementAndGet());
            case PRESERVE -> {
                if (request.sourceOid() != null && !request.sourceOid().isBlank()) {
                    yield request.sourceOid();
                }
                yield Long.toString(integerSequence.incrementAndGet());
            }
            case UUID -> UUID.randomUUID().toString();
            case DETERMINISTIC_UUID -> generateDeterministicUuid(request);
            case EXTERNAL -> throw new UnsupportedOperationException("EXTERNAL OID strategy is not yet implemented");
        };
    }

    private String generateDeterministicUuid(OidGenerationRequest request) {
        String namespace = request.namespace() != null ? request.namespace() : "default";

        if (request.identityValues() != null
                && !request.identityValues().isEmpty()
                && request.identityValues().values().stream().allMatch(CanonicalValue::defined)) {
            return generateIdentityDeterministicUuid(namespace, request);
        }

        return generateFallbackDeterministicUuid(namespace, request);
    }

    private static String generateIdentityDeterministicUuid(String namespace, OidGenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(namespace);
        sb.append("::");
        sb.append(request.ruleId());
        sb.append("::");
        sb.append(request.sourceClass());

        request.identityValues().forEach((key, value) -> {
            sb.append("::");
            sb.append(key);
            sb.append("=");
            sb.append(value.canonicalText());
        });

        return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private String generateFallbackDeterministicUuid(String namespace, OidGenerationRequest request) {
        if (request.sourceOid() == null || request.sourceOid().isBlank()) {
            String fallbackName = namespace + "::" + request.ruleId() + "::"
                    + request.inputId() + "::" + request.sourceBasketId() + "::"
                    + request.sourceClass() + "::FALLBACK::" + fallbackSequence.incrementAndGet();
            return UUID.nameUUIDFromBytes(fallbackName.getBytes(StandardCharsets.UTF_8))
                    .toString();
        }

        String name = namespace + "::" + request.ruleId() + "::"
                + request.inputId() + "::" + request.sourceBasketId() + "::"
                + request.sourceClass() + "::" + request.sourceOid();

        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
