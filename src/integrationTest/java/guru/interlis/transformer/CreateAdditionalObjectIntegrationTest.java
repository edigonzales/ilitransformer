package guru.interlis.transformer;

import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateAdditionalObjectIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";

    private static TypeSystemFacade testModelTs;
    private static TransferDescription transferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(
                "src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors: %s", result.diagnostics())
                .isFalse();
        transferDescription = result.transferDescription();
        testModelTs = new TypeSystemFacade(transferDescription);
    }

    @Test
    void createGeneratesAdditionalTargetObject() throws Exception {
        JobConfig config = configWithCreate();
        Map<String, TypeSystemFacade> sourceTs = Map.of("TestModel", testModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(1);
        assertThat(plan.rules().get(0).creates()).hasSize(1);
        assertThat(plan.rules().get(0).creates().get(0).targetClass().getScopedName())
                .isEqualTo("TestModel.TestTopic.TestClass");

        Iom_jObject src1 = new Iom_jObject("TestModel.TestTopic.TestClass", "1");
        src1.setattrvalue("Name", "Alice");
        src1.setattrvalue("Beschreibung", "Source description A");

        Iom_jObject src2 = new Iom_jObject("TestModel.TestTopic.TestClass", "2");
        src2.setattrvalue("Name", "Bob");
        src2.setattrvalue("Beschreibung", "Source description B");

        Path outputPath = Files.createTempFile("create-test-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, transferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            java.util.function.Function<String, IoxReader> readerFactory = inputId -> {
                try {
                    return mockReader(src1, src2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            TransformResult result = engine.runTyped(plan, readerFactory,
                    Map.of("out1", writer));

            assertThat(result.sourceRecordsRead()).isEqualTo(2);
            assertThat(result.sourceRecordsFiltered()).isEqualTo(0);
            assertThat(result.errors()).isEqualTo(0);
            assertThat(engineDiag.all()).isEmpty();
            assertThat(result.targetsCreated()).isGreaterThanOrEqualTo(4);
            assertThat(result.targetsWritten()).isGreaterThanOrEqualTo(4);

            assertThat(outputPath.toFile()).exists();
            String content = Files.readString(outputPath);
            assertThat(content).contains("TestModel:TestClass");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void createAssignsValuesCorrectly() throws Exception {
        JobConfig config = configWithCreateAndAssign();
        Map<String, TypeSystemFacade> sourceTs = Map.of("TestModel", testModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.rules().get(0).creates()).hasSize(1);

        Iom_jObject src1 = new Iom_jObject("TestModel.TestTopic.TestClass", "1");
        src1.setattrvalue("Name", "Alice");
        src1.setattrvalue("Beschreibung", "Desc A");

        Path outputPath = Files.createTempFile("create-assign-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, transferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            java.util.function.Function<String, IoxReader> readerFactory = inputId -> {
                try {
                    return mockReader(src1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            TransformResult result = engine.runTyped(plan, readerFactory,
                    Map.of("out1", writer));

            assertThat(result.errors()).isEqualTo(0);
            assertThat(result.targetsCreated()).isGreaterThanOrEqualTo(2);

            String content = Files.readString(outputPath);
            assertThat(content).contains("TestModel:TestClass");
            assertThat(content).contains("Alice");
            assertThat(content).contains("Desc A");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void createWithUnknownClassIsDiagnosed() throws Exception {
        JobConfig config = configBase();
        JobConfig.CreateSpec badCreate = new JobConfig.CreateSpec();
        badCreate.clazz = "TestModel.TestTopic.NonExistentClass";
        config.mapping.rules.get(0).create = List.of(badCreate);

        Map<String, TypeSystemFacade> sourceTs = Map.of("TestModel", testModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);

        assertThat(plan.diagnostics().hasErrors()).isTrue();
        assertThat(plan.diagnostics().all()).anyMatch(d ->
                d.code().equals(guru.interlis.transformer.diag.DiagnosticCode.MAP_CREATE_UNKNOWN_CLASS));
    }

    // -- Helpers -------------------------------------------------------------

    private static JobConfig configBase() {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.model = "TestModel";
        in.path = "input.xtf";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "TestModel";
        out.path = "output.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "copy-rule";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "TestModel.TestTopic.TestClass";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "TestModel.TestTopic.TestClass";
        src.inputs = List.of("in1");
        rule.sources.add(src);

        config.mapping.rules.add(rule);
        return config;
    }

    private static JobConfig configWithCreate() {
        JobConfig config = configBase();

        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Name", "${s.Name}");

        JobConfig.CreateSpec create = new JobConfig.CreateSpec();
        create.clazz = "TestModel.TestTopic.TestClass";
        rule.create = List.of(create);

        return config;
    }

    private static JobConfig configWithCreateAndAssign() {
        JobConfig config = configBase();

        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Name", "${s.Name}");

        JobConfig.CreateSpec create = new JobConfig.CreateSpec();
        create.clazz = "TestModel.TestTopic.TestClass";
        create.assign = new LinkedHashMap<>();
        create.assign.put("Name", "${s.Name}");
        create.assign.put("Beschreibung", "${s.Beschreibung}");
        rule.create = List.of(create);

        return config;
    }

    private IoxReader mockReader(Iom_jObject obj1, Iom_jObject obj2) throws Exception {
        IoxReader reader = mock(IoxReader.class);
        when(reader.read()).thenReturn(
                new ch.interlis.iox_j.StartTransferEvent("test", null, null),
                new ch.interlis.iox_j.StartBasketEvent("TestModel.TestTopic", "b1"),
                new ch.interlis.iox_j.ObjectEvent(obj1),
                new ch.interlis.iox_j.ObjectEvent(obj2),
                new ch.interlis.iox_j.EndBasketEvent(),
                new ch.interlis.iox_j.EndTransferEvent(),
                null, null, null, null, null, null, null, null, null, null
        );
        return reader;
    }

    private IoxReader mockReader(Iom_jObject obj1) throws Exception {
        IoxReader reader = mock(IoxReader.class);
        when(reader.read()).thenReturn(
                new ch.interlis.iox_j.StartTransferEvent("test", null, null),
                new ch.interlis.iox_j.StartBasketEvent("TestModel.TestTopic", "b1"),
                new ch.interlis.iox_j.ObjectEvent(obj1),
                new ch.interlis.iox_j.EndBasketEvent(),
                new ch.interlis.iox_j.EndTransferEvent(),
                null, null, null, null, null, null, null, null, null, null
        );
        return reader;
    }
}
