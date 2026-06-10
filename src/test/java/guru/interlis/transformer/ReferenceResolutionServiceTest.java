package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.ReferenceResolutionReport;
import guru.interlis.transformer.engine.ReferenceResolutionService;
import guru.interlis.transformer.mapping.plan.*;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ReferenceResolutionServiceTest {

    private static final TransformPlan EMPTY_PLAN = new TransformPlan(
            "test", "forward", FailPolicy.STRICT, CompileMode.STRICT,
            List.of(), Map.of(), Map.of(),
            new DiagnosticCollector(),
            new OidPlan(OidStrategy.PRESERVE, "ns"),
            new BasketPlan(BasketStrategy.PRESERVE),
            Map.of());

    @Test
    void forwardReference() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        // Register target objects
        Iom_jObject owner = new Iom_jObject("Model.T.ClassA", "t1");
        Iom_jObject referenced = new Iom_jObject("Model.T.ClassB", "t2");

        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassA", "t1");
        stateStore.registerTarget(ownerKey, owner);
        stateStore.registerTarget(new TargetObjectKey("out1", "Model.T.ClassB", "t2"), referenced);

        // Index referenced object in ReferenceIndex
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.ClassB", "oid2"),
                new TargetReference("out1", "Model.T.ClassB", "t2", "rule-b"));

        // Add deferred reference: ClassA -> ClassB
        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "oid2"),
                null, "Model.T.ClassB",
                new DeferredReference.Cardinality(0, 1),
                false));

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.resolved()).isEqualTo(1);
        assertThat(report.ambiguous()).isZero();
        assertThat(report.unresolvedOptional()).isZero();
        assertThat(report.unresolvedMandatory()).isZero();
        assertThat(report.typeMismatch()).isZero();
        assertThat(diag.all()).isEmpty();

        // Verify ref was set on owner
        IomObject refObj = owner.getattrobj("roleB", 0);
        assertThat(refObj).isNotNull();
        assertThat(refObj.getobjectrefoid()).isEqualTo("t2");
    }

    @Test
    void backwardReference() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        // ClassB target (created first, but its ref points to ClassA which is resolved later)
        Iom_jObject owner = new Iom_jObject("Model.T.ClassB", "t2");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassB", "t2");
        stateStore.registerTarget(ownerKey, owner);

        // The referenced ClassA was created earlier
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.ClassA", "oid1"),
                new TargetReference("out1", "Model.T.ClassA", "t1", "rule-a"));

        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "backRef", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassA", "oid1"),
                null, "Model.T.ClassA",
                new DeferredReference.Cardinality(0, 1),
                false));

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.resolved()).isEqualTo(1);
        assertThat(diag.all()).isEmpty();

        IomObject refObj = owner.getattrobj("backRef", 0);
        assertThat(refObj).isNotNull();
        assertThat(refObj.getobjectrefoid()).isEqualTo("t1");
    }

    @Test
    void missingOptionalReference() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        Iom_jObject owner = new Iom_jObject("Model.T.ClassA", "t1");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassA", "t1");
        stateStore.registerTarget(ownerKey, owner);

        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "NONEXISTENT"),
                null, null,
                new DeferredReference.Cardinality(0, 1),
                false)); // optional

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.unresolvedOptional()).isEqualTo(1);
        assertThat(report.unresolvedMandatory()).isZero();
        assertThat(diag.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.RUN_REF_UNRESOLVED));
    }

    @Test
    void missingMandatoryReference() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        Iom_jObject owner = new Iom_jObject("Model.T.ClassA", "t1");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassA", "t1");
        stateStore.registerTarget(ownerKey, owner);

        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "NONEXISTENT"),
                null, null,
                new DeferredReference.Cardinality(1, 1),
                true)); // mandatory

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.unresolvedMandatory()).isEqualTo(1);
        assertThat(diag.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY));
    }

    @Test
    void ambiguousReference() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        Iom_jObject owner = new Iom_jObject("Model.T.ClassA", "t1");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassA", "t1");
        stateStore.registerTarget(ownerKey, owner);

        // Two targets with same source key
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.ClassB", "oid2"),
                new TargetReference("out1", "Model.T.ClassB", "t2", "rule-b"));
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.ClassB", "oid2"),
                new TargetReference("out1", "Model.T.ClassB", "t3", "rule-b"));

        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "oid2"),
                null, null,
                new DeferredReference.Cardinality(0, 1),
                false));

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.ambiguous()).isEqualTo(1);
        assertThat(diag.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.RUN_REF_AMBIGUOUS));
    }

    @Test
    void targetRuleDisambiguatesSameSourceObject() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        Iom_jObject owner = new Iom_jObject("Model.T.Label", "label1");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.Label", "label1");
        stateStore.registerTarget(ownerKey, owner);

        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.Point", "oid1"),
                new TargetReference("out1", "Model.T.Point", "point1", "point-rule"));
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.Point", "oid1"),
                new TargetReference("out1", "Model.T.Symbol", "symbol1", "symbol-rule"));

        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "labelOf", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.Point", "oid1"),
                "point-rule", "Model.T.Point",
                new DeferredReference.Cardinality(1, 1),
                true));

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.resolved()).isEqualTo(1);
        assertThat(report.ambiguous()).isZero();
        assertThat(diag.all()).isEmpty();
        assertThat(owner.getattrobj("labelOf", 0).getobjectrefoid()).isEqualTo("point1");
    }

    @Test
    void wrongTargetClassReference() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        Iom_jObject owner = new Iom_jObject("Model.T.ClassA", "t1");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassA", "t1");
        stateStore.registerTarget(ownerKey, owner);

        // Source resolves to ClassC but we expect ClassB
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.ClassB", "oid2"),
                new TargetReference("out1", "Model.T.ClassC", "t2", "rule-c"));

        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "oid2"),
                null, "Model.T.ClassB", // expected
                new DeferredReference.Cardinality(0, 1),
                false));

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.typeMismatch()).isEqualTo(1);
        assertThat(diag.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.RUN_REF_TYPE_MISMATCH));
    }

    @Test
    void perOwnerCardinalityViolation() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        Iom_jObject owner = new Iom_jObject("Model.T.ClassA", "t1");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassA", "t1");
        stateStore.registerTarget(ownerKey, owner);

        // Two different targets both matching our lookup
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.ClassB", "oid2"),
                new TargetReference("out1", "Model.T.ClassB", "t2", "rule-b"));
        refIndex.add(new SourceObjectKey("in1", "b1", "Model.T.ClassB", "oid3"),
                new TargetReference("out1", "Model.T.ClassB", "t3", "rule-b"));

        // Max cardinality is 1, but we add 2 refs
        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "oid2"),
                null, "Model.T.ClassB",
                new DeferredReference.Cardinality(0, 1),
                false));
        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", null,
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "oid3"),
                null, "Model.T.ClassB",
                new DeferredReference.Cardinality(0, 1),
                false));

        ReferenceResolutionService service = new ReferenceResolutionService();
        ReferenceResolutionReport report = service.resolveAll(
                EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(report.cardinalityViolations()).isEqualTo(1);
        assertThat(report.resolved()).isEqualTo(1);
        assertThat(diag.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.RUN_REF_CARDINALITY));
    }

    @Test
    void associationNameInDiagnostic() {
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryReferenceIndex refIndex = new InMemoryReferenceIndex();
        DiagnosticCollector diag = new DiagnosticCollector();

        Iom_jObject owner = new Iom_jObject("Model.T.ClassA", "t1");
        TargetObjectKey ownerKey = new TargetObjectKey("out1", "Model.T.ClassA", "t1");
        stateStore.registerTarget(ownerKey, owner);

        stateStore.addDeferredReference(new DeferredReference(
                ownerKey, "roleB", "MyAssociation",
                new SourceReferenceSelector("in1", "b1", "Model.T.ClassB", "NONEXISTENT"),
                null, null,
                new DeferredReference.Cardinality(0, 1),
                false));

        ReferenceResolutionService service = new ReferenceResolutionService();
        service.resolveAll(EMPTY_PLAN, stateStore, refIndex, diag);

        assertThat(diag.all()).anyMatch(d ->
                d.message().contains("MyAssociation"));
    }
}
