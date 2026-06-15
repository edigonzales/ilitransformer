package guru.interlis.transformer;

import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.state.DuplicateTargetOidException;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.state.TargetObjectKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DuplicateTargetOidTest {

    private final InMemoryStateStore store = new InMemoryStateStore();

    @Test
    void firstRegistrationSucceeds() {
        TargetObjectKey key = new TargetObjectKey("out1", "A.B.C", "oid-1");
        Iom_jObject obj = new Iom_jObject("A.B.C", "oid-1");

        assertThatCode(() -> store.registerTarget(key, obj)).doesNotThrowAnyException();
        assertThat(store.targetExists(key)).isTrue();
        assertThat(store.findTarget(key)).isPresent();
        assertThat(store.findTarget(key).get().getobjectoid()).isEqualTo("oid-1");
    }

    @Test
    void duplicateRegistrationThrows() {
        TargetObjectKey key = new TargetObjectKey("out1", "A.B.C", "dup-oid");
        Iom_jObject obj1 = new Iom_jObject("A.B.C", "dup-oid");
        Iom_jObject obj2 = new Iom_jObject("A.B.C", "dup-oid");

        store.registerTarget(key, obj1);
        assertThatThrownBy(() -> store.registerTarget(key, obj2))
                .isInstanceOf(DuplicateTargetOidException.class)
                .hasMessageContaining("dup-oid");
    }

    @Test
    void allowsSameTargetClassAndOidInDifferentOutputs() {
        TargetObjectKey key1 = new TargetObjectKey("out1", "A.B.C", "oid-1");
        TargetObjectKey key2 = new TargetObjectKey("out2", "A.B.C", "oid-1");
        Iom_jObject obj1 = new Iom_jObject("A.B.C", "oid-1");
        Iom_jObject obj2 = new Iom_jObject("A.B.C", "oid-1");

        store.registerTarget(key1, obj1);
        assertThatCode(() -> store.registerTarget(key2, obj2)).doesNotThrowAnyException();
    }

    @Test
    void findTargetUsesOutputIdWhenProvided() {
        TargetObjectKey key1 = new TargetObjectKey("out1", "A.B.C", "oid-1");
        TargetObjectKey key2 = new TargetObjectKey("out2", "A.B.C", "oid-1");
        Iom_jObject obj1 = new Iom_jObject("A.B.C", "oid-1");
        Iom_jObject obj2 = new Iom_jObject("A.B.C", "oid-1");

        store.registerTarget(key1, obj1);
        store.registerTarget(key2, obj2);

        assertThat(store.findTarget(key1)).isPresent();
        assertThat(store.findTarget(key1).get()).isSameAs(obj1);
        assertThat(store.findTarget(key2)).isPresent();
        assertThat(store.findTarget(key2).get()).isSameAs(obj2);
    }

    @Test
    void legacyFindTargetObjectFindsTargetWithOutputId() {
        TargetObjectKey key = new TargetObjectKey("out1", "A.B.C", "oid-1");
        Iom_jObject obj = new Iom_jObject("A.B.C", "oid-1");
        store.registerTarget(key, obj);

        assertThat(store.findTargetObject("A.B.C", "oid-1")).isPresent();
        assertThat(store.findTargetObject("A.B.C", "oid-1").get()).isSameAs(obj);
    }

    @Test
    void sameOidDifferentClassIsAllowed() {
        TargetObjectKey key1 = new TargetObjectKey("out1", "A.B.C1", "oid-1");
        TargetObjectKey key2 = new TargetObjectKey("out1", "A.B.C2", "oid-1");
        Iom_jObject obj1 = new Iom_jObject("A.B.C1", "oid-1");
        Iom_jObject obj2 = new Iom_jObject("A.B.C2", "oid-1");

        assertThatCode(() -> store.registerTarget(key1, obj1)).doesNotThrowAnyException();
        assertThatCode(() -> store.registerTarget(key2, obj2)).doesNotThrowAnyException();
    }

    @Test
    void targetExistsReturnsFalseForUnknownKey() {
        TargetObjectKey key = new TargetObjectKey("out1", "A.B.C", "unknown");
        assertThat(store.targetExists(key)).isFalse();
    }

    @Test
    void findTargetReturnsEmptyForUnknownKey() {
        TargetObjectKey key = new TargetObjectKey("out1", "A.B.C", "unknown");
        assertThat(store.findTarget(key)).isEmpty();
    }

    @Test
    void backwardCompatIndexTargetObjectStillWorks() {
        Iom_jObject obj = new Iom_jObject("A.B.C", "bw-oid");
        store.indexTargetObject("A.B.C", "bw-oid", obj);
        assertThat(store.findTargetObject("A.B.C", "bw-oid")).isPresent();
    }

    @Test
    void backwardCompatIndexTargetObjectDetectsDuplicate() {
        Iom_jObject obj1 = new Iom_jObject("A.B.C", "dup-oid");
        Iom_jObject obj2 = new Iom_jObject("A.B.C", "dup-oid");

        store.indexTargetObject("A.B.C", "dup-oid", obj1);
        assertThatThrownBy(() -> store.indexTargetObject("A.B.C", "dup-oid", obj2))
                .isInstanceOf(DuplicateTargetOidException.class);
    }
}
