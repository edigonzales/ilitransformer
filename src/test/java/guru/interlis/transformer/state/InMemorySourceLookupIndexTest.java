package guru.interlis.transformer.state;

import ch.interlis.iom_j.Iom_jObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySourceLookupIndexTest {

    @Test
    void scalarLookupFindsRecord() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord("dm01", "Model.T.ClassA", "1", "Name", "TestName"));

        List<SourceRecord> hits = index.lookup(
                new LookupKey(null, "Model.T.ClassA", "Name",
                        new CanonicalValue("text", "TestName", true)));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).sourceObject().getattrvalue("Name")).isEqualTo("TestName");
    }

    @Test
    void referenceObjectLookupFindsRecord() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        Iom_jObject obj = new Iom_jObject("Model.T.ClassA", "1");
        obj.addattrobj("RefAttr", Iom_jObject.REF).setobjectrefoid("REF_OID_42");

        index.index(new SourceRecord("dm01", null, "Model.T.ClassA", obj));

        List<SourceRecord> hits = index.lookup(
                new LookupKey(null, "Model.T.ClassA", "RefAttr",
                        new CanonicalValue("text", "REF_OID_42", true)));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).sourceObject().getobjectoid()).isEqualTo("1");
    }

    @Test
    void inputIdFilterWorksCorrectly() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord("input-A", "Model.T.ClassA", "1", "Name", "ValueA"));
        index.index(sourceRecord("input-B", "Model.T.ClassA", "2", "Name", "ValueA"));

        List<SourceRecord> hitsA = index.lookup(
                new LookupKey("input-A", "Model.T.ClassA", "Name",
                        new CanonicalValue("text", "ValueA", true)));
        assertThat(hitsA).hasSize(1);
        assertThat(hitsA.get(0).sourceFileId()).isEqualTo("input-A");

        List<SourceRecord> hitsB = index.lookup(
                new LookupKey("input-B", "Model.T.ClassA", "Name",
                        new CanonicalValue("text", "ValueA", true)));
        assertThat(hitsB).hasSize(1);
        assertThat(hitsB.get(0).sourceFileId()).isEqualTo("input-B");
    }

    @Test
    void nonExistentValueReturnsEmptyList() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord("dm01", "Model.T.ClassA", "1", "Name", "Present"));

        List<SourceRecord> hits = index.lookup(
                new LookupKey(null, "Model.T.ClassA", "Name",
                        new CanonicalValue("text", "NonExistent", true)));

        assertThat(hits).isEmpty();
    }

    @Test
    void multipleRecordsWithSameValueAreAllReturned() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord("dm01", "Model.T.ClassA", "1", "Name", "Shared"));
        index.index(sourceRecord("dm01", "Model.T.ClassA", "2", "Name", "Shared"));

        List<SourceRecord> hits = index.lookup(
                new LookupKey(null, "Model.T.ClassA", "Name",
                        new CanonicalValue("text", "Shared", true)));

        assertThat(hits).hasSize(2);
        assertThat(hits).extracting(r -> r.sourceObject().getobjectoid())
                .containsExactlyInAnyOrder("1", "2");
    }

    private static SourceRecord sourceRecord(String inputId, String tag, String oid,
                                             String attrName, String attrValue) {
        Iom_jObject object = new Iom_jObject(tag, oid);
        object.setattrvalue(attrName, attrValue);
        return new SourceRecord(inputId, null, tag, object);
    }
}
