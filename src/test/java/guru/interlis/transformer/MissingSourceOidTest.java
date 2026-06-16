package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidStrategy;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class MissingSourceOidTest {

    private final DefaultOidGenerationService service = new DefaultOidGenerationService();

    @Test
    void deterministicUuidWithoutSourceOidUsesFallbackSequence() {
        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", null, new LinkedHashMap<>(), null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", null, new LinkedHashMap<>(), null);

        String oid1 = service.generate(req1);
        String oid2 = service.generate(req2);

        assertThat(oid1).isNotNull();
        assertThat(oid2).isNotNull();
        assertThat(oid1).isNotEqualTo(oid2);
    }

    @Test
    void deterministicUuidWithBlankSourceOidUsesFallbackSequence() {
        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", "", new LinkedHashMap<>(), null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", "", new LinkedHashMap<>(), null);

        String oid1 = service.generate(req1);
        String oid2 = service.generate(req2);

        assertThat(oid1).isNotEqualTo(oid2);
    }

    @Test
    void preserveStrategyWithoutSourceOidFallsBackToInteger() {
        OidGenerationRequest req = new OidGenerationRequest(
                OidStrategy.PRESERVE, null, "r1", "in1", "b1", "C", null, new LinkedHashMap<>(), null);

        String oid = service.generate(req);
        assertThat(oid).isNotNull();
        assertThat(oid).isEqualTo("1");
    }
}
