package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ReferenceResolutionIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade p7Ts;
    private static TransferDescription p7TransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(
                "src/test/data/models/with-references.ili", MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors: %s", result.diagnostics())
                .isFalse();
        p7TransferDescription = result.transferDescription();
        p7Ts = new TypeSystemFacade(p7TransferDescription);
    }

    @Test
    void resolvesRefBetweenClassAandClassB() throws Exception {
        JobConfig config = p7Config(true);
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(2);

        Iom_jObject srcB = new Iom_jObject("P7Model.P7Topic.ClassB", "b1");
        srcB.setattrvalue("Name", "TargetB");

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "SourceA");
        srcA.setattrvalue("RefToB", "b1");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("b1");
        System.out.println("srcA tag=" + srcA.getobjecttag() + " oid=" + srcA.getobjectoid());

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        var capturedOids = new ArrayList<String>();
        var capturedRefs = new ArrayList<String>();
        IoxWriter writer = new CapturingRefWriter(capturedOids, capturedRefs);

        TransformResult result = engine.runTyped(plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader(srcB);
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(result.sourceRecordsRead()).isEqualTo(2);
        assertThat(result.targetsCreated()).isEqualTo(2);
        assertThat(result.targetsWritten()).isEqualTo(2);
        assertThat(result.errors()).isZero();

        assertThat(capturedOids).hasSize(2);
        assertThat(capturedRefs).hasSize(1);
        assertThat(capturedRefs.get(0)).isIn(capturedOids);
    }

    @Test
    void emitsUnresolvedDiagnosticWhenRefNotFound() throws Exception {
        JobConfig config = p7Config(true);
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "Orphan");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("NONEXISTENT");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(result.targetsCreated()).isEqualTo(1);
        assertThat(engineDiag.all()).isNotEmpty();
        assertThat(engineDiag.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY));
    }

    @Test
    void emitsMissingMandatoryWhenRequiredRefHasNoSourceValue() throws Exception {
        JobConfig config = p7Config(true);
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "NoRef");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(result.targetsCreated()).isEqualTo(1);
        assertThat(engineDiag.all()).isNotEmpty();
        assertThat(engineDiag.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY));
    }

    @Test
    void failPolicyStrictEmitsErrorForTypeMismatch() throws Exception {
        JobConfig config = p7Config(true);
        config.job.failPolicy = "strict";
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.failPolicy()).isEqualTo(guru.interlis.transformer.mapping.plan.FailPolicy.STRICT);

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "SourceA");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("MISSING");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(engineDiag.all()).isNotEmpty();
        assertThat(result.errors()).isGreaterThan(0);
    }

    @Test
    void failPolicyLenientEmitsWarningNotError() throws Exception {
        JobConfig config = p7Config(true);
        config.job.failPolicy = "lenient";
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.failPolicy()).isEqualTo(guru.interlis.transformer.mapping.plan.FailPolicy.LENIENT);

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "SourceA");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("MISSING");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(engineDiag.all()).isNotEmpty();
        assertThat(result.errors()).isZero();
    }

    // -- Helpers -------------------------------------------------------------

    private JobConfig p7Config(boolean required) {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec inA = new JobConfig.InputSpec();
        inA.id = "in-classa";
        inA.model = "P7Model";
        inA.path = "inputA.xtf";
        config.job.inputs.add(inA);

        JobConfig.InputSpec inB = new JobConfig.InputSpec();
        inB.id = "in-classb";
        inB.model = "P7Model";
        inB.path = "inputB.xtf";
        config.job.inputs.add(inB);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "P7Model";
        out.path = "output.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec ruleA = new JobConfig.RuleSpec();
        ruleA.id = "r-classa";
        ruleA.target = new JobConfig.TargetSpec();
        ruleA.target.clazz = "P7Model.P7Topic.ClassA";
        ruleA.target.output = "out1";
        JobConfig.SourceSpec srcA = new JobConfig.SourceSpec();
        srcA.alias = "p";
        srcA.clazz = "P7Model.P7Topic.ClassA";
        srcA.inputs = List.of("in-classa");
        ruleA.sources.add(srcA);
        ruleA.assign = new java.util.LinkedHashMap<>();
        ruleA.assign.put("Name", "${p.Name}");

        JobConfig.RuleSpec ruleB = new JobConfig.RuleSpec();
        ruleB.id = "r-classb";
        ruleB.target = new JobConfig.TargetSpec();
        ruleB.target.clazz = "P7Model.P7Topic.ClassB";
        ruleB.target.output = "out1";
        JobConfig.SourceSpec srcB = new JobConfig.SourceSpec();
        srcB.alias = "b";
        srcB.clazz = "P7Model.P7Topic.ClassB";
        srcB.inputs = List.of("in-classb");
        ruleB.sources.add(srcB);
        ruleB.assign = new java.util.LinkedHashMap<>();
        ruleB.assign.put("Name", "${b.Name}");

        JobConfig.RefMapping refMapping = new JobConfig.RefMapping();
        refMapping.role = "A_Role";
        refMapping.sourceRef = "p.RefToB";
        refMapping.required = required;
        ruleA.refs = List.of(refMapping);
        config.mapping.rules.add(ruleA);
        config.mapping.rules.add(ruleB);
        return config;
    }

    private IoxReader createMockReader(Iom_jObject... objects) {
        return new IoxReader() {
            private final IoxEvent[] events;
            {
                var list = new ArrayList<IoxEvent>();
                list.add(new ch.interlis.iox_j.StartTransferEvent("test", null, null));
                list.add(new ch.interlis.iox_j.StartBasketEvent("P7Model.P7Topic", "b1"));
                for (Iom_jObject obj : objects) {
                    list.add(new ch.interlis.iox_j.ObjectEvent(obj));
                }
                list.add(new ch.interlis.iox_j.EndBasketEvent());
                list.add(new ch.interlis.iox_j.EndTransferEvent());
                events = list.toArray(new IoxEvent[0]);
            }
            private int index;

            @Override
            public IoxEvent read() {
                if (index < events.length) return events[index++];
                return null;
            }

            @Override
            public void close() {}

            @Override
            public IomObject createIomObject(String tag, String oid) {
                return new Iom_jObject(tag, oid);
            }

            @Override
            public ch.interlis.iox.IoxFactoryCollection getFactory() { return null; }

            @Override
            public void setFactory(ch.interlis.iox.IoxFactoryCollection factory) {}
        };
    }

    private static class CapturingRefWriter implements IoxWriter {
        final List<String> capturedOids;
        final List<String> capturedRefs;

        CapturingRefWriter(List<String> capturedOids, List<String> capturedRefs) {
            this.capturedOids = capturedOids;
            this.capturedRefs = capturedRefs;
        }

        @Override
        public void write(IoxEvent event) {
            if (event instanceof ch.interlis.iox.ObjectEvent oe) {
                IomObject obj = oe.getIomObject();
                capturedOids.add(obj.getobjectoid());
                for (int i = 0; i < obj.getattrcount(); i++) {
                    var attrName = obj.getattrname(i);
                    IomObject attrValue = obj.getattrobj(attrName, 0);
                    if (attrValue != null && attrValue.getobjectrefoid() != null) {
                        capturedRefs.add(attrValue.getobjectrefoid());
                    }
                }
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        @Override
        public IomObject createIomObject(String tag, String oid) {
            return new Iom_jObject(tag, oid);
        }

        @Override
        public ch.interlis.iox.IoxFactoryCollection getFactory() { return null; }

        @Override
        public void setFactory(ch.interlis.iox.IoxFactoryCollection factory) {}
    }
}
