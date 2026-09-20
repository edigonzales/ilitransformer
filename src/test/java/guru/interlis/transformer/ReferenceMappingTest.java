package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.state.TargetObjectKey;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxWriter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReferenceMappingTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade associationTs;

    @BeforeAll
    static void compileModel() {
        IliModelCompileResult result = new IliModelService().compileModel(MODELDIR + "with-associations.ili", MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors: %s", result.diagnostics().all())
                .isFalse();
        associationTs = new TypeSystemFacade(result.transferDescription());
    }

    @Test
    void sourceRefEvaluatesOidFunctionForJoinedSource() throws Exception {
        TransformPlan plan = compileMapping(oidExpressionConfig());
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent = object("AssocModel.AssocTopic.Parent", "p1", "same");
        Iom_jObject child = object("AssocModel.AssocTopic.Child", "c1", "same");
        InMemoryStateStore stateStore = new InMemoryStateStore();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(new ExpressionEngine(), stateStore, diagnostics);

        engine.runTyped(plan, inputId -> TestMockReaders.mockReader(parent, child), Map.of("out", recordingWriter()));

        IomObject target = stateStore
                .findTarget(new TargetObjectKey("out", "AssocModel.AssocTopic.Parent", "p1"))
                .orElseThrow();
        assertThat(target.getattrobj("ChildRole", 0).getobjectrefoid()).isEqualTo("c1");
        assertThat(diagnostics.hasErrors()).isFalse();
    }

    @Test
    void compositionIsRebuiltFromChildToParentSourceRole() throws Exception {
        TransformPlan plan = compileMapping(compositionConfig());
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent = object("AssocModel.AssocTopic.Parent", "p1", "Parent");
        Iom_jObject child = object("AssocModel.AssocTopic.Child", "c1", "Child");
        child.addattrobj("ParentRole", Iom_jObject.REF).setobjectrefoid("p1");

        InMemoryStateStore stateStore = new InMemoryStateStore();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(new ExpressionEngine(), stateStore, diagnostics);

        engine.runTyped(plan, inputId -> TestMockReaders.mockReader(parent, child), Map.of("out", recordingWriter()));

        IomObject target = stateStore
                .findTarget(new TargetObjectKey("out", "AssocModel.AssocTopic.Child", "c1"))
                .orElseThrow();
        assertThat(target.getattrobj("ParentRole", 0).getobjectrefoid()).isEqualTo("p1");
        assertThat(diagnostics.hasErrors()).isFalse();
    }

    @Test
    void sourceRefReportsUnknownAliasDuringCompilation() {
        TransformPlan plan = compileMapping(compositionConfig("oid(unknown)"));

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE));
    }

    private static TransformPlan compileMapping(JobConfig config) {
        return new MappingCompiler()
                .compileTyped(config, Map.of("AssocModel", associationTs), Map.of("AssocModel", associationTs));
    }

    private static JobConfig oidExpressionConfig() {
        JobConfig config = baseConfig();
        config.mapping.rules.add(rule(
                "child",
                "AssocModel.AssocTopic.Child",
                source("c", "AssocModel.AssocTopic.Child"),
                Map.of("Name", "c.Name"),
                null));

        JobConfig.RuleSpec parent = rule(
                "parent",
                "AssocModel.AssocTopic.Parent",
                source("p", "AssocModel.AssocTopic.Parent"),
                Map.of("Name", "p.Name"),
                null);
        JobConfig.SourceSpec joinedChild = source("c", "AssocModel.AssocTopic.Child");
        parent.sources.add(joinedChild);
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "p";
        join.right = "c";
        join.on = "p.Name == c.Name";
        join.type = "inner";
        parent.joins = List.of(join);
        parent.refs = List.of(ref("ChildRole", "oid(c)", "child", false));
        config.mapping.rules.add(parent);
        return config;
    }

    private static JobConfig compositionConfig() {
        return compositionConfig("c.ParentRole");
    }

    private static JobConfig compositionConfig(String sourceRef) {
        JobConfig config = baseConfig();
        config.mapping.rules.add(rule(
                "parent",
                "AssocModel.AssocTopic.Parent",
                source("p", "AssocModel.AssocTopic.Parent"),
                Map.of("Name", "p.Name"),
                null));
        JobConfig.RuleSpec child = rule(
                "child",
                "AssocModel.AssocTopic.Child",
                source("c", "AssocModel.AssocTopic.Child"),
                Map.of("Name", "c.Name"),
                null);
        child.refs = List.of(ref("ParentRole", sourceRef, "parent", true));
        config.mapping.rules.add(child);
        return config;
    }

    private static JobConfig baseConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.id = "in";
        input.model = "AssocModel";
        config.job.inputs.add(input);

        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.id = "out";
        output.model = "AssocModel";
        config.job.outputs.add(output);

        config.mapping.oidStrategy.defaultStrategy = "preserve";
        return config;
    }

    private static JobConfig.RuleSpec rule(
            String id,
            String targetClass,
            JobConfig.SourceSpec source,
            Map<String, String> assignments,
            JobConfig.JoinSpec join) {
        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = id;
        rule.target = new JobConfig.TargetSpec();
        rule.target.output = "out";
        rule.target.clazz = targetClass;
        rule.sources.add(source);
        rule.assign = assignments;
        if (join != null) {
            rule.joins = List.of(join);
        }
        return rule;
    }

    private static JobConfig.SourceSpec source(String alias, String className) {
        JobConfig.SourceSpec source = new JobConfig.SourceSpec();
        source.alias = alias;
        source.inputs = List.of("in");
        source.clazz = className;
        return source;
    }

    private static JobConfig.RefMapping ref(String role, String sourceRef, String targetRule, boolean required) {
        JobConfig.RefMapping ref = new JobConfig.RefMapping();
        ref.association = "ParentChild";
        ref.role = role;
        ref.sourceRef = sourceRef;
        ref.targetRule = targetRule;
        ref.required = required;
        return ref;
    }

    private static Iom_jObject object(String className, String oid, String name) {
        Iom_jObject object = new Iom_jObject(className, oid);
        object.setattrvalue("Name", name);
        return object;
    }

    private static IoxWriter recordingWriter() throws Exception {
        IoxWriter writer = mock(IoxWriter.class);
        doAnswer(invocation -> {
                    return null;
                })
                .when(writer)
                .write(any(IoxEvent.class));
        return writer;
    }
}
