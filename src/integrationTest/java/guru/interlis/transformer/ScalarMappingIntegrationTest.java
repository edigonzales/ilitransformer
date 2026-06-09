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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScalarMappingIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";

    private static TypeSystemFacade p5ModelTs;
    private static TransferDescription p5TransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(
                "src/test/data/models/p5-test.ili", MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors: %s", result.diagnostics())
                .isFalse();
        p5TransferDescription = result.transferDescription();
        p5ModelTs = new TypeSystemFacade(p5TransferDescription);
    }

    // -- Engine test with mocked reader + real writer ------------------------

    @Test
    void transformsScalarAttributes1to1() throws Exception {
        JobConfig config = p5Config(null);
        Map<String, TypeSystemFacade> sourceTs = Map.of("P5Model", p5ModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("P5Model", p5ModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(1);
        assertThat(plan.rules().get(0).sources()).hasSize(1);
        assertThat(plan.rules().get(0).sources().get(0).inputIds()).containsExactly("in1");
        assertThat(plan.rules().get(0).sources().get(0).sourceClass()).isNotNull();
        assertThat(plan.rules().get(0).assignments()).hasSize(3);

        // Create mock source objects
        Iom_jObject src1 = new Iom_jObject("P5Model.P5Topic.SourceClass", "1");
        src1.setattrvalue("Name", "Alice");
        src1.setattrvalue("Anzahl", "42");
        src1.setattrvalue("Aktiv", "true");

        Iom_jObject src2 = new Iom_jObject("P5Model.P5Topic.SourceClass", "2");
        src2.setattrvalue("Name", "Bob");
        src2.setattrvalue("Anzahl", "7");
        src2.setattrvalue("Aktiv", "false");

        Path outputPath = Files.createTempFile("p5-test-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, p5TransferDescription);

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
            assertThat(result.targetsCreated()).isEqualTo(2);
            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);
            assertThat(engineDiag.all()).isEmpty();

            // Verify output file exists and has content
            assertThat(outputPath.toFile()).exists();
            String content = Files.readString(outputPath);
            assertThat(content).contains("TargetClass");
            assertThat(content).contains("Alice");
            assertThat(content).contains("Bob");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void whereFilterExcludesAllObjects() throws Exception {
        JobConfig config = p5Config("${s.Name} == null");
        Map<String, TypeSystemFacade> sourceTs = Map.of("P5Model", p5ModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("P5Model", p5ModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.rules().get(0).sources().get(0).where()).isEqualTo("${s.Name} == null");

        Iom_jObject src1 = new Iom_jObject("P5Model.P5Topic.SourceClass", "1");
        src1.setattrvalue("Name", "Alice");
        Iom_jObject src2 = new Iom_jObject("P5Model.P5Topic.SourceClass", "2");
        src2.setattrvalue("Name", "Bob");

        Path outputPath = Files.createTempFile("p5-filtered-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, p5TransferDescription);

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
            TransformResult result = engine.runTyped(plan,
                    readerFactory,
                    Map.of("out1", writer));

            assertThat(result.sourceRecordsRead()).isEqualTo(2);
            assertThat(result.sourceRecordsFiltered()).isEqualTo(2);
            assertThat(result.targetsCreated()).isEqualTo(0);
            assertThat(result.targetsWritten()).isEqualTo(0);

            System.out.println(result.summary());
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void whereFilterKeepsAllWithDefined() throws Exception {
        JobConfig config = p5Config("defined(${s.Name})");
        Map<String, TypeSystemFacade> sourceTs = Map.of("P5Model", p5ModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("P5Model", p5ModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject src1 = new Iom_jObject("P5Model.P5Topic.SourceClass", "1");
        src1.setattrvalue("Name", "Alice");
        src1.setattrvalue("Anzahl", "1");
        src1.setattrvalue("Aktiv", "true");
        Iom_jObject src2 = new Iom_jObject("P5Model.P5Topic.SourceClass", "2");
        src2.setattrvalue("Name", "Bob");
        src2.setattrvalue("Anzahl", "2");
        src2.setattrvalue("Aktiv", "false");

        Path outputPath = Files.createTempFile("p5-defined-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, p5TransferDescription);

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
            TransformResult result = engine.runTyped(plan,
                    readerFactory,
                    Map.of("out1", writer));

            assertThat(result.sourceRecordsRead()).isEqualTo(2);
            assertThat(result.sourceRecordsFiltered()).isEqualTo(0);
            assertThat(result.targetsCreated()).isEqualTo(2);
            assertThat(result.targetsWritten()).isEqualTo(2);
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    // -- Helpers -------------------------------------------------------------

    private JobConfig p5Config(String where) {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.model = "P5Model";
        in.path = "input.xtf";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "P5Model";
        out.path = "output.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "copy-rule";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "P5Model.P5Topic.TargetClass";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "P5Model.P5Topic.SourceClass";
        src.inputs = List.of("in1");
        if (where != null) {
            src.where = where;
        }
        rule.sources.add(src);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Label", "${s.Name}");
        rule.assign.put("Size", "${s.Anzahl}");
        rule.assign.put("Enabled", "${s.Aktiv}");
        config.mapping.rules.add(rule);
        return config;
    }

    private IoxReader mockReader(Iom_jObject obj1, Iom_jObject obj2) throws Exception {
        IoxReader reader = mock(IoxReader.class);
        when(reader.read()).thenReturn(
                new ch.interlis.iox_j.StartTransferEvent("test", null, null),
                new ch.interlis.iox_j.StartBasketEvent("P5Model.P5Topic", "b1"),
                new ch.interlis.iox_j.ObjectEvent(obj1),
                new ch.interlis.iox_j.ObjectEvent(obj2),
                new ch.interlis.iox_j.EndBasketEvent(),
                new ch.interlis.iox_j.EndTransferEvent(),
                null,
                null,
                null,
                null

        );
        return reader;
    }
}
