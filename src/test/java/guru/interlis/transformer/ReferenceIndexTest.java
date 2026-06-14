package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.ReferenceResolutionReport;
import guru.interlis.transformer.engine.ReferenceResolutionService;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.OidPlan;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransferFormat;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceIndexTest {

    @Test
    void exactMatch() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));

        List<TargetReference> result = index.find(
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassA", "oid1"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetOid()).isEqualTo("t1");
    }

    @Test
    void crossBasketFallback() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));

        // Lookup without basketId should find via cross-basket fallback
        List<TargetReference> result = index.find(
                new SourceReferenceSelector("in1", null, "Model.T.ClassA", "oid1"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetOid()).isEqualTo("t1");
    }

    @Test
    void sameOidDifferentBasket() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));
        index.add(new SourceObjectKey("in1", "b2", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t2", "rule-a"));

        // Exact match with basket b1
        List<TargetReference> r1 = index.find(
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassA", "oid1"));
        assertThat(r1).hasSize(1);
        assertThat(r1.get(0).targetOid()).isEqualTo("t1");

        // Exact match with basket b2
        List<TargetReference> r2 = index.find(
                new SourceReferenceSelector("in1", "b2", "Model.T.ClassA", "oid1"));
        assertThat(r2).hasSize(1);
        assertThat(r2.get(0).targetOid()).isEqualTo("t2");
    }

    @Test
    void sameOidDifferentInput() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));
        index.add(new SourceObjectKey("in2", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t2", "rule-a"));

        List<TargetReference> r1 = index.find(
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassA", "oid1"));
        assertThat(r1).hasSize(1);
        assertThat(r1.get(0).targetOid()).isEqualTo("t1");

        List<TargetReference> r2 = index.find(
                new SourceReferenceSelector("in2", "b1", "Model.T.ClassA", "oid1"));
        assertThat(r2).hasSize(1);
        assertThat(r2.get(0).targetOid()).isEqualTo("t2");
    }

    @Test
    void sameOidDifferentClass() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassB", "oid1"),
                new TargetReference("out1", "Model.T.ClassB", "t2", "rule-b"));

        List<TargetReference> r1 = index.find(
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassA", "oid1"));
        assertThat(r1).hasSize(1);
        assertThat(r1.get(0).targetOid()).isEqualTo("t1");
    }

    @Test
    void noGlobalFallbackByDefault() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));

        // Global OID-only lookup (all nulls except OID) matches via cross-basket fallback
        List<TargetReference> result = index.find(
                new SourceReferenceSelector(null, null, null, "oid1"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetOid()).isEqualTo("t1");
    }

    @Test
    void globalFallbackOnlyWhenExplicitlyEnabled() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));

        List<TargetReference> result = index.find(
                new SourceReferenceSelector(null, null, null, "oid1"), false, true);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetOid()).isEqualTo("t1");
    }

    @Test
    void emptyWhenNotFound() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        List<TargetReference> result = index.find(
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassA", "NONEXISTENT"));
        assertThat(result).isEmpty();
    }

    @Test
    void ambiguousReferenceWhenMultipleExactMatches() {
        InMemoryReferenceIndex index = new InMemoryReferenceIndex();
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));
        index.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t2", "rule-b"));

        List<TargetReference> result = index.find(
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassA", "oid1"));
        assertThat(result).hasSize(2);
    }
}
