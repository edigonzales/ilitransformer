package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GeometryIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MAPPING_FILE = "src/test/resources/mappings/dm01-to-dmav-geom-test.yaml";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dm01Result = service.compileModel(
                "src/test/data/models/dm01-geom-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DM01 geometry model compilation errors:\n  " + errors);
        }
        dm01Ts = new TypeSystemFacade(dm01Result.transferDescription());

        IliModelCompileResult dmavResult = service.compileModel(
                "src/test/data/models/dmav-geom-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DMAV geometry model compilation errors:\n  " + errors);
        }
        dmavTs = new TypeSystemFacade(dmavResult.transferDescription());
    }

    @Test
    void compileGeometryMappingDetectsCoordType() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01GeomTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavGeomTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);

        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();

        // Verify geometry attribute is classified as COORD, not TEXT
        AssignmentPlan geomAp = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Geometrie"))
                .findFirst().orElseThrow();
        assertThat(geomAp.expectedType()).isEqualTo(TypeInfo.COORD);
    }

    @Test
    void geometryCoordPassThroughProducesValidOutput() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01GeomTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavGeomTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject lfp = new Iom_jObject("Dm01GeomTestModel.Fixpunkte.LFP3", "1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        lfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        lfp.setattrvalue("Lagegenauigkeit", "5.0");

        Path outputPath = Files.createTempFile("dmav-geom-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath,
                    dmavTs.getTransferDescription());

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan,
                    onceReaderFactory(lfp), Map.of("dmav", writer));

            assertThat(result.targetsWritten()).isEqualTo(1);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("LFP3");
            assertThat(content).contains("LFP001");
            assertThat(content).contains("geom:coord");
            assertThat(content).contains("2600000.0");
            assertThat(content).contains("1200000.0");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    // -- Helpers -------------------------------------------------------------

    private static IoxReader createMockReader(Iom_jObject... objects) {
        return new IoxReader() {
            private int index = 0;
            private final IoxEvent[] events;

            {
                var list = new ArrayList<IoxEvent>();
                list.add(new StartTransferEvent("test", null, null));
                list.add(new StartBasketEvent("Dm01GeomTestModel.Fixpunkte", "b1"));
                for (Iom_jObject obj : objects) {
                    list.add(new ObjectEvent(obj));
                }
                list.add(new EndBasketEvent());
                list.add(new EndTransferEvent());
                events = list.toArray(new IoxEvent[0]);
            }

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

    private static java.util.function.Function<String, IoxReader> onceReaderFactory(Iom_jObject... objects) {
        return new java.util.function.Function<>() {
            private boolean used = false;
            @Override
            public IoxReader apply(String inputId) {
                if (used) {
                    return new IoxReader() {
                        @Override public IoxEvent read() { return null; }
                        @Override public void close() {}
                        @Override public IomObject createIomObject(String tag, String oid) { return new Iom_jObject(tag, oid); }
                        @Override public ch.interlis.iox.IoxFactoryCollection getFactory() { return null; }
                        @Override public void setFactory(ch.interlis.iox.IoxFactoryCollection f) {}
                    };
                }
                used = true;
                return createMockReader(objects);
            }
        };
    }
}
