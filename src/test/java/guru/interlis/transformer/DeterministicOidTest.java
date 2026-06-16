package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.state.CanonicalValue;
import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidStrategy;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DeterministicOidTest {

    private final DefaultOidGenerationService service = new DefaultOidGenerationService();

    @Test
    void sameIdentityKeysProducesSameOid() {
        LinkedHashMap<String, CanonicalValue> keys = new LinkedHashMap<>();
        keys.put("NBIdent", new CanonicalValue("text", "ABC123", true));
        keys.put("Nummer", new CanonicalValue("numeric", "42", true));

        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID,
                "dm01-to-dmav",
                "lfp3",
                "in1",
                "b1",
                "P5Model.P5Topic.SourceClass",
                "src-1",
                keys,
                "UUIDOID");
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID,
                "dm01-to-dmav",
                "lfp3",
                "in1",
                "b1",
                "P5Model.P5Topic.SourceClass",
                "src-1",
                keys,
                "UUIDOID");

        String oid1 = service.generate(req1);
        String oid2 = service.generate(req2);

        assertThat(oid1).isEqualTo(oid2);
        assertThatCode(() -> UUID.fromString(oid1)).doesNotThrowAnyException();
    }

    @Test
    void twoObjectsWithoutIdentityKeysProduceDifferentOids() {
        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID,
                "ns",
                "r1",
                "in1",
                "b1",
                "X.Y.C1",
                "oid-1",
                new LinkedHashMap<>(),
                null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID,
                "ns",
                "r1",
                "in1",
                "b1",
                "X.Y.C1",
                "oid-2",
                new LinkedHashMap<>(),
                null);

        String oid1 = service.generate(req1);
        String oid2 = service.generate(req2);

        assertThat(oid1).isNotEqualTo(oid2);
    }

    @Test
    void differentRuleIdProducesDifferentOid() {
        LinkedHashMap<String, CanonicalValue> keys = new LinkedHashMap<>();
        keys.put("NBIdent", new CanonicalValue("text", "ABC123", true));

        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "lfp3", "in1", "b1", "C", "src-1", keys, "UUIDOID");
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "lfp3-nachfuehrung", "in1", "b1", "C", "src-1", keys, "UUIDOID");

        assertThat(service.generate(req1)).isNotEqualTo(service.generate(req2));
    }

    @Test
    void differentNamespaceProducesDifferentOid() {
        LinkedHashMap<String, CanonicalValue> keys = new LinkedHashMap<>();
        keys.put("NBIdent", new CanonicalValue("text", "ABC123", true));

        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns-1", "r1", "in1", "b1", "C", "src-1", keys, null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns-2", "r1", "in1", "b1", "C", "src-1", keys, null);

        assertThat(service.generate(req1)).isNotEqualTo(service.generate(req2));
    }

    @Test
    void differentKeyValuesProducesDifferentOid() {
        LinkedHashMap<String, CanonicalValue> keys1 = new LinkedHashMap<>();
        keys1.put("NBIdent", new CanonicalValue("text", "ABC123", true));
        LinkedHashMap<String, CanonicalValue> keys2 = new LinkedHashMap<>();
        keys2.put("NBIdent", new CanonicalValue("text", "XYZ789", true));

        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", "src-1", keys1, null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", "src-1", keys2, null);

        assertThat(service.generate(req1)).isNotEqualTo(service.generate(req2));
    }

    @Test
    void targetClassIsIncludedInHash() {
        LinkedHashMap<String, CanonicalValue> keys = new LinkedHashMap<>();
        keys.put("Name", new CanonicalValue("text", "Test", true));

        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "A.B.Target1", "src", keys, null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "A.B.Target2", "src", keys, null);

        assertThat(service.generate(req1)).isNotEqualTo(service.generate(req2));
    }

    @Test
    void fallbackWithoutIdentityKeysUsesSourceOid() {
        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID,
                "ns",
                "r1",
                "in1",
                "b1",
                "C",
                "SOURCE-123",
                new LinkedHashMap<>(),
                null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID,
                "ns",
                "r1",
                "in1",
                "b1",
                "C",
                "SOURCE-123",
                new LinkedHashMap<>(),
                null);

        assertThat(service.generate(req1)).isEqualTo(service.generate(req2));
    }

    @Test
    void partiallyDefinedIdentityKeysFallbackToSourceOid() {
        LinkedHashMap<String, CanonicalValue> keys = new LinkedHashMap<>();
        keys.put("Name", new CanonicalValue("text", "Test", true));
        keys.put("EmptyKey", new CanonicalValue("text", "", false));

        OidGenerationRequest req1 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", "SOURCE-1", keys, null);
        OidGenerationRequest req2 = new OidGenerationRequest(
                OidStrategy.DETERMINISTIC_UUID, "ns", "r1", "in1", "b1", "C", "SOURCE-1", keys, null);

        assertThat(service.generate(req1)).isEqualTo(service.generate(req2));
    }
}
