package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidStrategy;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class PreserveOidTest {

    private final DefaultOidGenerationService service = new DefaultOidGenerationService();

    @Test
    void preserveCopiesSourceOid() {
        OidGenerationRequest req = new OidGenerationRequest(
                OidStrategy.PRESERVE, null, "r1", "in1", "b1", "C", "SOURCE-42", new LinkedHashMap<>(), null);
        assertThat(service.generate(req)).isEqualTo("SOURCE-42");
    }

    @Test
    void preservePreservesNonNumericOids() {
        OidGenerationRequest req = new OidGenerationRequest(
                OidStrategy.PRESERVE, null, "r1", "in1", "b1", "C", "CH.ABCD.1234", new LinkedHashMap<>(), null);
        assertThat(service.generate(req)).isEqualTo("CH.ABCD.1234");
    }

    @Test
    void preserveFallsBackToIntegerWhenSourceOidIsNull() {
        OidGenerationRequest req = new OidGenerationRequest(
                OidStrategy.PRESERVE, null, "r1", "in1", "b1", "C", null, new LinkedHashMap<>(), null);
        String oid = service.generate(req);
        assertThat(oid).isNotNull();
        assertThat(oid).isEqualTo("1");
    }

    @Test
    void preserveFallsBackToIntegerWhenSourceOidIsBlank() {
        OidGenerationRequest req = new OidGenerationRequest(
                OidStrategy.PRESERVE, null, "r1", "in1", "b1", "C", "   ", new LinkedHashMap<>(), null);
        assertThat(service.generate(req)).isEqualTo("1");
    }
}
