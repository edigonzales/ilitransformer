package guru.interlis.transformer.state;

import ch.interlis.iom_j.Iom_jObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryStateStoreTest {

    @Test
    void sourceRecordsFilteredByInputAndClass() {
        InMemoryStateStore store = new InMemoryStateStore();
        Iom_jObject obj1 = new Iom_jObject("Model.T.ClassA", "1");
        Iom_jObject obj2 = new Iom_jObject("Model.T.ClassA", "2");
        Iom_jObject obj3 = new Iom_jObject("Model.T.ClassB", "3");

        store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassA", obj1));
        store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassA", obj2));
        store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassB", obj3));

        List<SourceRecord> result = store.sourceRecords("in1", "Model.T.ClassA");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).sourceObject().getobjectoid()).isEqualTo("1");
        assertThat(result.get(1).sourceObject().getobjectoid()).isEqualTo("2");
    }

    @Test
    void sourceRecordsEmptyForWrongInputId() {
        InMemoryStateStore store = new InMemoryStateStore();
        store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassA",
                new Iom_jObject("Model.T.ClassA", "1")));

        List<SourceRecord> result = store.sourceRecords("unknown", "Model.T.ClassA");

        assertThat(result).isEmpty();
    }

    @Test
    void sourceRecordsEmptyForWrongClass() {
        InMemoryStateStore store = new InMemoryStateStore();
        store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassA",
                new Iom_jObject("Model.T.ClassA", "1")));

        List<SourceRecord> result = store.sourceRecords("in1", "Model.T.ClassX");

        assertThat(result).isEmpty();
    }

    @Test
    void sourceRecordsPreservesInsertionOrder() {
        InMemoryStateStore store = new InMemoryStateStore();
        for (int i = 1; i <= 5; i++) {
            store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassA",
                    new Iom_jObject("Model.T.ClassA", String.valueOf(i))));
            store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassB",
                    new Iom_jObject("Model.T.ClassB", String.valueOf(i))));
        }

        List<SourceRecord> resultA = store.sourceRecords("in1", "Model.T.ClassA");
        List<SourceRecord> resultB = store.sourceRecords("in1", "Model.T.ClassB");

        assertThat(resultA).hasSize(5);
        assertThat(resultB).hasSize(5);
        assertThat(resultA.get(0).sourceObject().getobjectoid()).isEqualTo("1");
        assertThat(resultA.get(4).sourceObject().getobjectoid()).isEqualTo("5");
        assertThat(resultB.get(0).sourceObject().getobjectoid()).isEqualTo("1");
        assertThat(resultB.get(4).sourceObject().getobjectoid()).isEqualTo("5");
    }

    @Test
    void sourceRecordsReturnsImmutableList() {
        InMemoryStateStore store = new InMemoryStateStore();
        store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassA",
                new Iom_jObject("Model.T.ClassA", "1")));

        List<SourceRecord> result = store.sourceRecords("in1", "Model.T.ClassA");

        assertThat(result).isNotEmpty();
        assertThrows(UnsupportedOperationException.class, () -> result.add(
                new SourceRecord("in1", null, "Model.T.ClassA",
                        new Iom_jObject("Model.T.ClassA", "2"))));
    }

    @Test
    void unfilteredSourceRecordsStillContainsAll() {
        InMemoryStateStore store = new InMemoryStateStore();
        store.addSourceRecord(new SourceRecord("in1", null, "Model.T.ClassA",
                new Iom_jObject("Model.T.ClassA", "1")));
        store.addSourceRecord(new SourceRecord("in2", null, "Model.T.ClassB",
                new Iom_jObject("Model.T.ClassB", "2")));

        List<SourceRecord> all = store.sourceRecords();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).sourceFileId()).isEqualTo("in1");
        assertThat(all.get(1).sourceFileId()).isEqualTo("in2");
    }
}
