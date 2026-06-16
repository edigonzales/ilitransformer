package guru.interlis.transformer.engine;

import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.state.SourceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SourceIndexingServiceTest {

    @Test
    @DisplayName("SourceRecord with ParentContext stores all fields correctly")
    void sourceRecordWithParentContext() {
        Iom_jObject obj = new Iom_jObject("Model.T.Child", "child-1");
        obj.setattrvalue("Name", "Child1");

        SourceRecord.ParentContext parentCtx = new SourceRecord.ParentContext("parent-1", "Model.T.Parent");
        SourceRecord sr = new SourceRecord("in1", "b1", "Model.T.Child", obj, parentCtx);

        assertThat(sr.sourceFileId()).isEqualTo("in1");
        assertThat(sr.sourceBasketId()).isEqualTo("b1");
        assertThat(sr.sourceClass()).isEqualTo("Model.T.Child");
        assertThat(sr.sourceObject()).isSameAs(obj);
        assertThat(sr.parentContext()).isEqualTo(parentCtx);
        assertThat(sr.parentContext().parentOid()).isEqualTo("parent-1");
        assertThat(sr.parentContext().parentClass()).isEqualTo("Model.T.Parent");
    }

    @Test
    @DisplayName("Backward-compatible 4-arg constructor defaults parentContext to null")
    void backwardCompatibleConstructor() {
        Iom_jObject obj = new Iom_jObject("Model.T.ClassA", "1");
        SourceRecord sr = new SourceRecord("in1", null, "Model.T.ClassA", obj);

        assertThat(sr.parentContext()).isNull();
    }

    @Test
    @DisplayName("parentContextOptional is empty when parentContext is null")
    void parentContextOptionalEmptyWhenNull() {
        Iom_jObject obj = new Iom_jObject("Model.T.ClassA", "1");
        SourceRecord sr = new SourceRecord("in1", null, "Model.T.ClassA", obj);

        assertThat(sr.parentContextOptional()).isEmpty();
    }

    @Test
    @DisplayName("parentContextOptional is present when parentContext is set")
    void parentContextOptionalPresentWhenSet() {
        Iom_jObject obj = new Iom_jObject("Model.T.Child", "child-1");
        SourceRecord.ParentContext parentCtx = new SourceRecord.ParentContext("p1", "Model.T.Parent");
        SourceRecord sr = new SourceRecord("in1", "b1", "Model.T.Child", obj, parentCtx);

        Optional<SourceRecord.ParentContext> result = sr.parentContextOptional();
        assertThat(result).isPresent();
        assertThat(result.get().parentOid()).isEqualTo("p1");
        assertThat(result.get().parentClass()).isEqualTo("Model.T.Parent");
    }

    @Test
    @DisplayName("ParentContext record implements value equality")
    void parentContextEquality() {
        SourceRecord.ParentContext a = new SourceRecord.ParentContext("oid-1", "ClassA");
        SourceRecord.ParentContext b = new SourceRecord.ParentContext("oid-1", "ClassA");
        SourceRecord.ParentContext c = new SourceRecord.ParentContext("oid-2", "ClassA");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
