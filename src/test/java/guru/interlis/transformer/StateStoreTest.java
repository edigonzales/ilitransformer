package guru.interlis.transformer;

import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.state.SourceRefKey;
import guru.interlis.transformer.state.TargetRefValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateStoreTest {
    @Test
    void resolvesExactAndFallbackMappings() {
        InMemoryStateStore store = new InMemoryStateStore();
        store.putIdMapping(new SourceRefKey("A.C", "10", "f1", "b1"), new TargetRefValue("T.C", "99", "out", "b1"));

        assertThat(store.findIdMappings("A.C", "10", "f1", "b1")).hasSize(1);
        assertThat(store.findIdMappings("A.C", "10", "other", "other")).isEmpty();
    }
}
