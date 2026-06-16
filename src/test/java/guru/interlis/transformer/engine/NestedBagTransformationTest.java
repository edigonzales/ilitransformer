package guru.interlis.transformer.engine;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.ExpressionParser;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.BasketPlan;
import guru.interlis.transformer.mapping.plan.CompileMode;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.IdentityPlan;
import guru.interlis.transformer.mapping.plan.OidPlan;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.state.BasketStrategy;
import guru.interlis.transformer.state.InMemoryParentChildIndex;
import guru.interlis.transformer.state.InMemoryReferenceIndex;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidGenerationService;
import guru.interlis.transformer.state.OidStrategy;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.TargetObjectKey;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NestedBagTransformationTest {

    @Test
    void embedNestsChildBagInsideStructure() {
        // Setup: Parent object
        Iom_jObject parentTarget = new Iom_jObject("Test.Parent", "parent-1");

        // Setup: Source records for outer bag (Child -> Parent reference)
        Iom_jObject child1 = new Iom_jObject("Test.Child", "c1");
        child1.addattrobj("ParentRef", "Test.Parent").setobjectrefoid("parent-1");
        child1.setattrvalue("Name", "Child1");

        Iom_jObject child2 = new Iom_jObject("Test.Child", "c2");
        child2.addattrobj("ParentRef", "Test.Parent").setobjectrefoid("parent-1");
        child2.setattrvalue("Name", "Child2");

        // Setup: Source records for nested bag (GrandChild -> Child reference)
        Iom_jObject gc1 = new Iom_jObject("Test.GrandChild", "gc1");
        gc1.addattrobj("ChildRef", "Test.Child").setobjectrefoid("c1");
        gc1.setattrvalue("Detail", "DetailA");

        Iom_jObject gc2 = new Iom_jObject("Test.GrandChild", "gc2");
        gc2.addattrobj("ChildRef", "Test.Child").setobjectrefoid("c1");
        gc2.setattrvalue("Detail", "DetailB");

        Iom_jObject gc3 = new Iom_jObject("Test.GrandChild", "gc3");
        gc3.addattrobj("ChildRef", "Test.Child").setobjectrefoid("c2");
        gc3.setattrvalue("Detail", "DetailC");

        // State store + parent child index
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryParentChildIndex parentChildIndex = new InMemoryParentChildIndex();

        stateStore.addSourceRecord(new SourceRecord("in1", "b1", "Test.Child", child1));
        stateStore.addSourceRecord(new SourceRecord("in1", "b1", "Test.Child", child2));
        stateStore.addSourceRecord(new SourceRecord("in1", "b1", "Test.GrandChild", gc1));
        stateStore.addSourceRecord(new SourceRecord("in1", "b1", "Test.GrandChild", gc2));
        stateStore.addSourceRecord(new SourceRecord("in1", "b1", "Test.GrandChild", gc3));

        parentChildIndex.index(
                "Test.Child", "ParentRef", "parent-1", new SourceRecord("in1", "b1", "Test.Child", child1));
        parentChildIndex.index(
                "Test.Child", "ParentRef", "parent-1", new SourceRecord("in1", "b1", "Test.Child", child2));
        parentChildIndex.index(
                "Test.GrandChild", "ChildRef", "c1", new SourceRecord("in1", "b1", "Test.GrandChild", gc1));
        parentChildIndex.index(
                "Test.GrandChild", "ChildRef", "c1", new SourceRecord("in1", "b1", "Test.GrandChild", gc2));
        parentChildIndex.index(
                "Test.GrandChild", "ChildRef", "c2", new SourceRecord("in1", "b1", "Test.GrandChild", gc3));

        // Build bag plans
        List<AssignmentPlan> nestedAssignments = new ArrayList<>();
        guru.interlis.transformer.expr.Expression detailAst = ExpressionParser.parse("${gc.Detail}");
        nestedAssignments.add(new AssignmentPlan(
                "Detail",
                null,
                new CompiledExpression("${gc.Detail}", detailAst, TypeInfo.TEXT, true, java.util.Set.of())));

        // nested bag: parse real AST for where filter
        SourcePlan nestedSource = new SourcePlan("gc", null, List.of("in1"), null);
        guru.interlis.transformer.expr.Expression nestedAst = ExpressionParser.parse("refEquals(gc.ChildRef, ch)");
        CompiledExpression nestedWhere = new CompiledExpression(
                "refEquals(gc.ChildRef, ch)", nestedAst, TypeInfo.BOOLEAN, true, java.util.Set.of());
        BagPlan nestedBag = new BagPlan(
                "GrandChildren",
                nestedSource,
                "Test.GrandChild",
                nestedAssignments,
                nestedWhere,
                BagPlan.BagMode.EMBED,
                null, // no parentRefAttribute -> falls through to stateStore scan + where
                "ch", // parentAlias references outer bag's fromSource alias
                0,
                null,
                null,
                null,
                null,
                List.of());

        // Outer bag source plan
        SourcePlan outerSource = new SourcePlan("ch", null, List.of("in1"), null);
        guru.interlis.transformer.expr.Expression outerAst = ExpressionParser.parse("${ch.Name}");
        BagPlan outerBag = new BagPlan(
                "Children",
                outerSource,
                "Test.Child",
                List.of(new AssignmentPlan(
                        "Name",
                        null,
                        new CompiledExpression("${ch.Name}", outerAst, TypeInfo.TEXT, true, java.util.Set.of()))),
                null,
                BagPlan.BagMode.EMBED,
                "ParentRef",
                "p",
                0,
                null,
                null,
                null,
                null,
                List.of(nestedBag) // PASS NESTED BAGS
                );

        // Parent object binding
        Iom_jObject parentSourceObj = new Iom_jObject("Test.Parent", "parent-1");
        SourceRecord parentRecord = new SourceRecord("in1", "b1", "Test.Parent", parentSourceObj);
        SourcePlan parentSourcePlan = new SourcePlan("p", null, List.of("in1"), null);
        BoundSourceObject parentBound = new BoundSourceObject(parentSourcePlan, parentRecord);

        // TransformPlan stub
        TransformPlan transformPlan = new TransformPlan(
                "test",
                "test",
                guru.interlis.transformer.mapping.plan.FailPolicy.STRICT,
                guru.interlis.transformer.mapping.plan.CompileMode.COMPATIBLE,
                List.of(),
                Map.of(),
                Map.of(),
                new DiagnosticCollector(),
                null,
                null,
                Map.of());

        // RulePlan stub
        RulePlan rulePlan = new RulePlan(
                "test-rule",
                "out1",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of());

        // Bag execution context
        BagExecutionContext ctx = new BagExecutionContext(
                outerBag,
                parentBound,
                parentTarget,
                transformPlan,
                stateStore,
                parentChildIndex,
                new DiagnosticCollector(),
                rulePlan,
                new LinkedHashMap<>(),
                null,
                Map.of(),
                null);

        // Service with dummy OID generation
        guru.interlis.transformer.state.OidGenerationService oidGen =
                new guru.interlis.transformer.state.OidGenerationService() {
                    @Override
                    public String generate(guru.interlis.transformer.state.OidGenerationRequest request) {
                        return "oid-" + System.currentTimeMillis() + "-" + Math.random();
                    }
                };
        BagTransformationService service = new BagTransformationService(new ExpressionEngine(), oidGen);

        // Execute
        service.embed(ctx);

        // Verify outer structures
        assertThat(parentTarget.getattrvaluecount("Children")).isEqualTo(2);

        IomObject struct0 = parentTarget.getattrobj("Children", 0);
        assertThat(struct0.getattrvalue("Name")).isIn("Child1", "Child2");

        IomObject struct1 = parentTarget.getattrobj("Children", 1);
        assertThat(struct1.getattrvalue("Name")).isIn("Child1", "Child2");

        // Determine which struct has which grandchild count
        IomObject childWithGc1 = struct0.getattrvalue("Name").equals("Child1") ? struct0 : struct1;
        IomObject childWithGc2 = struct0.getattrvalue("Name").equals("Child2") ? struct0 : struct1;

        // Verify nested bags
        assertThat(childWithGc1.getattrvaluecount("GrandChildren")).isEqualTo(2);
        assertThat(childWithGc2.getattrvaluecount("GrandChildren")).isEqualTo(1);

        IomObject gcStruct0 = childWithGc1.getattrobj("GrandChildren", 0);
        assertThat(gcStruct0.getattrvalue("Detail")).isIn("DetailA", "DetailB");

        IomObject gcStruct1 = childWithGc1.getattrobj("GrandChildren", 1);
        assertThat(gcStruct1.getattrvalue("Detail")).isIn("DetailA", "DetailB");

        IomObject gcStruct2 = childWithGc2.getattrobj("GrandChildren", 0);
        assertThat(gcStruct2.getattrvalue("Detail")).isEqualTo("DetailC");
    }

    @Test
    void expandBindsNestedBackreferenceToExpandedParent() {
        Iom_jObject parentTarget = new Iom_jObject("Target.Parent", "parent-target-1");

        Iom_jObject nestedItem = new Iom_jObject("Source.NestedItem", null);
        nestedItem.setattrvalue("Detail", "NestedDetail");

        Iom_jObject childStruct = new Iom_jObject("Source.Child", null);
        childStruct.setattrvalue("Name", "Child1");
        childStruct.addattrobj("NestedItems", nestedItem);

        Iom_jObject parentSourceObj = new Iom_jObject("Source.Parent", "parent-source-1");
        parentSourceObj.addattrobj("Children", childStruct);

        SourceRecord parentRecord = new SourceRecord("in1", "b1", "Source.Parent", parentSourceObj);
        SourcePlan parentSourcePlan = new SourcePlan("p", null, List.of("in1"), null);

        CompiledExpression childName = new CompiledExpression(
                "${ch.Name}", ExpressionParser.parse("${ch.Name}"), TypeInfo.TEXT, true, java.util.Set.of());
        CompiledExpression nestedDetail = new CompiledExpression(
                "${gc.Detail}", ExpressionParser.parse("${gc.Detail}"), TypeInfo.TEXT, true, java.util.Set.of());

        BagPlan nestedExpand = new BagPlan(
                "NestedItems",
                new SourcePlan("gc", null, List.of("in1"), null),
                "Target.GrandChild",
                List.of(new AssignmentPlan("Detail", null, nestedDetail)),
                null,
                BagPlan.BagMode.EXPAND,
                null,
                "ch",
                null,
                null,
                null,
                new IdentityPlan(OidStrategy.INTEGER, "test", List.of()),
                new RefPlan("Child", null, "ch", "expand-rule", true),
                List.of());

        BagPlan outerExpand = new BagPlan(
                "Children",
                new SourcePlan("ch", null, List.of("in1"), null),
                "Target.Child",
                List.of(new AssignmentPlan("Name", null, childName)),
                null,
                BagPlan.BagMode.EXPAND,
                null,
                "p",
                null,
                null,
                null,
                new IdentityPlan(OidStrategy.INTEGER, "test", List.of()),
                new RefPlan("Parent", null, "p", "expand-rule", true),
                List.of(nestedExpand));

        TransformPlan transformPlan = new TransformPlan(
                "test",
                "test",
                FailPolicy.STRICT,
                CompileMode.COMPATIBLE,
                List.of(),
                Map.of(),
                Map.of(),
                new DiagnosticCollector(),
                new OidPlan(OidStrategy.INTEGER, "test"),
                new BasketPlan(BasketStrategy.PRESERVE),
                Map.of());

        RulePlan rulePlan = new RulePlan(
                "expand-rule",
                "out1",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of());

        InMemoryStateStore stateStore = new InMemoryStateStore();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        BagExecutionContext ctx = new BagExecutionContext(
                outerExpand,
                new BoundSourceObject(parentSourcePlan, parentRecord),
                parentTarget,
                transformPlan,
                stateStore,
                new InMemoryParentChildIndex(),
                diagnostics,
                rulePlan,
                new LinkedHashMap<>(),
                null,
                Map.of(),
                new InMemoryReferenceIndex());

        OidGenerationService oidGenerator = new OidGenerationService() {
            private int next = 0;

            @Override
            public String generate(OidGenerationRequest request) {
                next++;
                return "oid-" + next;
            }
        };

        BagTransformationService service = new BagTransformationService(new ExpressionEngine(), oidGenerator);
        service.expand(ctx);

        IomObject childTarget = stateStore
                .findTarget(new TargetObjectKey("out1", "Target.Child", "oid-1"))
                .orElseThrow();
        IomObject grandChildTarget = stateStore
                .findTarget(new TargetObjectKey("out1", "Target.GrandChild", "oid-2"))
                .orElseThrow();

        assertThat(childTarget.getattrobj("Parent", 0).getobjectrefoid()).isEqualTo("parent-target-1");
        assertThat(grandChildTarget.getattrobj("Child", 0).getobjectrefoid()).isEqualTo("oid-1");
        assertThat(stateStore.deferredRefs()).isEmpty();
        assertThat(stateStore.deferredReferences()).isEmpty();
        assertThat(diagnostics.all()).isEmpty();
    }
}
