package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidStrategy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DefaultOidGenerationServiceTest {

    private final DefaultOidGenerationService service = new DefaultOidGenerationService();

    @Test
    void integerStrategyGeneratesSequentialNumbers() {
        String oid1 = service.generate(oidIntReq("in1", "r1", null));
        String oid2 = service.generate(oidIntReq("in1", "r1", null));
        String oid3 = service.generate(oidIntReq("in1", "r1", null));
        assertThat(oid1).isEqualTo("1");
        assertThat(oid2).isEqualTo("2");
        assertThat(oid3).isEqualTo("3");
    }

    @Test
    void uuidStrategyGeneratesValidUuids() {
        String oid1 = service.generate(oidReq(OidStrategy.UUID, "ns", "r1", "src-1"));
        String oid2 = service.generate(oidReq(OidStrategy.UUID, "ns", "r2", "src-2"));
        assertThat(oid1).isNotNull();
        assertThat(oid2).isNotNull();
        assertThat(oid1).isNotEqualTo(oid2);
        assertThatCode(() -> UUID.fromString(oid1)).doesNotThrowAnyException();
        assertThatCode(() -> UUID.fromString(oid2)).doesNotThrowAnyException();
    }

    @Test
    void externalStrategyThrows() {
        OidGenerationRequest req = oidReq(OidStrategy.EXTERNAL, "ns", "r1", "src-1");
        assertThatThrownBy(() -> service.generate(req))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("EXTERNAL");
    }

    @Test
    void preserveCopiesSourceOid() {
        String oid = service.generate(oidReq(OidStrategy.PRESERVE, null, "r1", "SOURCE-42"));
        assertThat(oid).isEqualTo("SOURCE-42");
    }

    @Test
    void deterministicUuidNullNamespaceUsesDefault() {
        var req1 = oidReq(OidStrategy.DETERMINISTIC_UUID, null, "r1", "src-1");
        var req2 = oidReq(OidStrategy.DETERMINISTIC_UUID, null, "r1", "src-1");
        assertThat(service.generate(req1)).isEqualTo(service.generate(req2));
    }

    private static OidGenerationRequest oidIntReq(String inputId, String ruleId, String sourceOid) {
        return new OidGenerationRequest(
                OidStrategy.INTEGER,
                null,
                ruleId,
                inputId,
                null,
                "X.Y.C",
                sourceOid,
                new java.util.LinkedHashMap<>(),
                null);
    }

    private static OidGenerationRequest oidReq(
            OidStrategy strategy, String namespace, String ruleId, String sourceOid) {
        return new OidGenerationRequest(
                strategy, namespace, ruleId, "in1", "b1", "X.Y.C", sourceOid, new java.util.LinkedHashMap<>(), null);
    }
}
