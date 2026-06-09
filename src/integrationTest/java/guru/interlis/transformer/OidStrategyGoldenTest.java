package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxEvent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OidStrategyGoldenTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade p5ModelTs;
    private static TransferDescription p5TransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(
                "src/test/data/models/p5-test.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();
        p5TransferDescription = result.transferDescription();
        p5ModelTs = new TypeSystemFacade(p5TransferDescription);
    }

    private TransformPlan compileConfig(JobConfig config) {
        Map<String, TypeSystemFacade> sourceTs = Map.of("P5Model", p5ModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("P5Model", p5ModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        return plan;
    }

    private JobConfig configWithStrategy(String oidStrategy, String namespace,
                                          String basketStrategy) {
        JobConfig config = new JobConfig();
        config.version = 1;

        config.mapping.oidStrategy.defaultStrategy = oidStrategy;
        config.mapping.oidStrategy.namespace = namespace;
        config.mapping.basketStrategy.defaultStrategy = basketStrategy;

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
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "P5Model.P5Topic.TargetClass";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "P5Model.P5Topic.SourceClass";
        src.inputs = List.of("in1");
        rule.sources.add(src);

        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Label", "${s.Name}");
        rule.assign.put("Size", "${s.Anzahl}");
        rule.assign.put("Enabled", "${s.Aktiv}");

        rule.identity = new JobConfig.IdentitySpec();
        rule.identity.sourceKey = List.of("s.Name", "s.Anzahl");

        config.mapping.rules.add(rule);
        return config;
    }

    @Test
    void integerStrategyGeneratesSequentialOidsInOutput() throws Exception {
        JobConfig config = configWithStrategy("integer", null, "preserve");
        TransformPlan plan = compileConfig(config);

        Iom_jObject src1 = new Iom_jObject("P5Model.P5Topic.SourceClass", "s1");
        src1.setattrvalue("Name", "Alice");
        src1.setattrvalue("Anzahl", "42");
        src1.setattrvalue("Aktiv", "true");

        Iom_jObject src2 = new Iom_jObject("P5Model.P5Topic.SourceClass", "s2");
        src2.setattrvalue("Name", "Bob");
        src2.setattrvalue("Anzahl", "7");
        src2.setattrvalue("Aktiv", "false");

        // Capture written OIDs from the writer
        List<String> writtenOids = new ArrayList<>();

        IoxReader reader = createMockReader(src1, src2);

        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), new DiagnosticCollector());

        IoxWriter writer = new CapturingWriter(writtenOids);

        TransformResult result = engine.runTyped(plan,
                ignored -> createMockReader(src1, src2),
                Map.of("out1", writer));

        assertThat(result.targetsCreated()).isEqualTo(2);

        // Integer strategy: OIDs should be "1" and "2"
        assertThat(writtenOids).containsExactly("1", "2");
    }

    @Test
    void uuidStrategyGeneratesUniqueUuidOids() throws Exception {
        JobConfig config = configWithStrategy("uuid", null, "preserve");
        TransformPlan plan = compileConfig(config);

        Iom_jObject src1 = new Iom_jObject("P5Model.P5Topic.SourceClass", "s1");
        src1.setattrvalue("Name", "Alice");
        src1.setattrvalue("Anzahl", "42");
        src1.setattrvalue("Aktiv", "true");

        Iom_jObject src2 = new Iom_jObject("P5Model.P5Topic.SourceClass", "s2");
        src2.setattrvalue("Name", "Bob");
        src2.setattrvalue("Anzahl", "7");
        src2.setattrvalue("Aktiv", "false");

        List<String> writtenOids = new ArrayList<>();

        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), new DiagnosticCollector());

        IoxWriter writer = new CapturingWriter(writtenOids);

        engine.runTyped(plan,
                ignored -> createMockReader(src1, src2),
                Map.of("out1", writer));

        assertThat(writtenOids).hasSize(2);
        assertThat(writtenOids.get(0)).isNotEqualTo(writtenOids.get(1));
        assertThatCode(() -> java.util.UUID.fromString(writtenOids.get(0))).doesNotThrowAnyException();
    }

    @Test
    void preserveCopiesSourceOidToTarget() throws Exception {
        JobConfig config = configWithStrategy("preserve", null, "preserve");
        TransformPlan plan = compileConfig(config);

        Iom_jObject src = new Iom_jObject("P5Model.P5Topic.SourceClass", "ORIGINAL-OID-123");
        src.setattrvalue("Name", "Alice");
        src.setattrvalue("Anzahl", "42");
        src.setattrvalue("Aktiv", "true");

        List<String> writtenOids = new ArrayList<>();

        TransformationEngine engine = new TransformationEngine(
                new ExpressionEngine(), new InMemoryStateStore(), new DiagnosticCollector());

        IoxWriter writer = new CapturingWriter(writtenOids);

        engine.runTyped(plan,
                ignored -> createMockReader(src),
                Map.of("out1", writer));

        assertThat(writtenOids).containsExactly("ORIGINAL-OID-123");
    }

    /** Capturing writer that intercepts ObjectEvents and stores OIDs. */
    private static class CapturingWriter implements IoxWriter {
        final List<String> capturedOids;

        CapturingWriter(List<String> capturedOids) {
            this.capturedOids = capturedOids;
        }

        @Override
        public void write(IoxEvent event) {
            if (event instanceof ch.interlis.iox.ObjectEvent oe) {
                capturedOids.add(oe.getIomObject().getobjectoid());
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

    private IoxReader createMockReader(Iom_jObject... objects) {
        return new IoxReader() {
            private final IoxEvent[] events;
            {
                var list = new ArrayList<IoxEvent>();
                list.add(new ch.interlis.iox_j.StartTransferEvent("test", null, null));
                list.add(new ch.interlis.iox_j.StartBasketEvent("P5Model.P5Topic", "b1"));
                for (Iom_jObject obj : objects) {
                    list.add(new ch.interlis.iox_j.ObjectEvent(obj));
                }
                list.add(new ch.interlis.iox_j.EndBasketEvent());
                list.add(new ch.interlis.iox_j.EndTransferEvent());
                events = list.toArray(new IoxEvent[0]);
            }
            private int index = 0;

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
}
