package guru.interlis.transformer;

import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.state.OidStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OidStrategyTest {

    private final InMemoryStateStore store = new InMemoryStateStore();

    @Test
    void integerStrategyGeneratesSequentialNumbers() {
        String oid1 = store.nextOid(OidStrategy.INTEGER, null, "r1", null, Map.of());
        String oid2 = store.nextOid(OidStrategy.INTEGER, null, "r1", null, Map.of());
        String oid3 = store.nextOid(OidStrategy.INTEGER, null, "r1", null, Map.of());

        assertThat(oid1).isEqualTo("1");
        assertThat(oid2).isEqualTo("2");
        assertThat(oid3).isEqualTo("3");
    }

    @Test
    void preserveStrategyCopiesSourceOid() {
        String oid = store.nextOid(OidStrategy.PRESERVE, null, "r1", "SOURCE-42", Map.of());
        assertThat(oid).isEqualTo("SOURCE-42");
    }

    @Test
    void preserveStrategyFallsBackToIntegerWhenSourceOidIsNull() {
        String oid = store.nextOid(OidStrategy.PRESERVE, null, "r1", null, Map.of());
        assertThat(oid).isEqualTo("1");
    }

    @Test
    void uuidStrategyGeneratesValidUuids() {
        String oid1 = store.nextOid(OidStrategy.UUID, null, "r1", null, Map.of());
        String oid2 = store.nextOid(OidStrategy.UUID, null, "r1", null, Map.of());

        assertThat(oid1).isNotNull();
        assertThat(oid2).isNotNull();
        assertThat(oid1).isNotEqualTo(oid2);
        assertThatCode(() -> UUID.fromString(oid1)).doesNotThrowAnyException();
        assertThatCode(() -> UUID.fromString(oid2)).doesNotThrowAnyException();
    }

    @Test
    void deterministicUuidSameInputProducesSameOid() {
        Map<String, String> keys = Map.of("NBIdent", "ABC123", "Nummer", "42");

        String oid1 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "dm01-to-dmav", "lfp3", "src-1", keys);
        String oid2 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "dm01-to-dmav", "lfp3", "src-1", keys);

        assertThat(oid1).isEqualTo(oid2);
        assertThatCode(() -> UUID.fromString(oid1)).doesNotThrowAnyException();
    }

    @Test
    void deterministicUuidDifferentRuleIdProducesDifferentOid() {
        Map<String, String> keys = Map.of("NBIdent", "ABC123");

        String oid1 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns", "lfp3", "src-1", keys);
        String oid2 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns", "lfp3-nachfuehrung", "src-1", keys);

        assertThat(oid1).isNotEqualTo(oid2);
    }

    @Test
    void deterministicUuidDifferentNamespaceProducesDifferentOid() {
        Map<String, String> keys = Map.of("NBIdent", "ABC123");

        String oid1 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns-1", "r1", "src-1", keys);
        String oid2 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns-2", "r1", "src-1", keys);

        assertThat(oid1).isNotEqualTo(oid2);
    }

    @Test
    void deterministicUuidDifferentKeyValuesProducesDifferentOid() {
        Map<String, String> keys1 = Map.of("NBIdent", "ABC123");
        Map<String, String> keys2 = Map.of("NBIdent", "XYZ789");

        String oid1 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns", "r1", "src-1", keys1);
        String oid2 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns", "r1", "src-1", keys2);

        assertThat(oid1).isNotEqualTo(oid2);
    }

    @Test
    void deterministicUuidNullNamespaceUsesDefault() {
        Map<String, String> keys = Map.of("NBIdent", "ABC123");

        String oid = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                null, "r1", "src-1", keys);

        assertThat(oid).isNotNull();
        assertThatCode(() -> UUID.fromString(oid)).doesNotThrowAnyException();
    }

    @Test
    void externalStrategyReturnsNull() {
        String oid = store.nextOid(OidStrategy.EXTERNAL, null, "r1", "src-1", Map.of());
        assertThat(oid).isNull();
    }

    @Test
    void deterministicUuidEmptyKeyValuesStillProducesDeterministicOid() {
        String oid1 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns", "r1", "src-1", Map.of());
        String oid2 = store.nextOid(OidStrategy.DETERMINISTIC_UUID,
                "ns", "r1", "src-1", Map.of());

        assertThat(oid1).isEqualTo(oid2);
    }
}
