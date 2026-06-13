package guru.interlis.transformer;

import ch.interlis.ili2c.metamodel.TransferDescription;
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
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
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

class Dm01ToDmavHfp3IntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MAPPING_FILE = "src/test/resources/mappings/dm01-to-dmav-hfp3-test.yaml";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;
    private static TransferDescription dmavTransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dm01Result = service.compileModel(MODELDIR + "dm01-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DM01 model compilation errors:\n  " + errors);
        }
        dm01Ts = new TypeSystemFacade(dm01Result.transferDescription());

        IliModelCompileResult dmavResult = service.compileModel(MODELDIR + "dmav-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DMAV model compilation errors:\n  " + errors);
        }
        dmavTransferDescription = dmavResult.transferDescription();
        dmavTs = new TypeSystemFacade(dmavTransferDescription);
    }

    @Test
    void compilesHfp3Mapping() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);

        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(2);
        assertThat(plan.enumMaps()).containsKeys("Zuverlaessigkeit_DM01_DMAV");
    }

    @Test
    void transformsHfp3WithEnumMappingAndDiv() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "HF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test-HFP3-Nachfuehrung");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject hfp = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3", "10");
        hfp.setattrvalue("Entstehung", "1");
        hfp.setattrvalue("NBIdent", "HF001");
        hfp.setattrvalue("Nummer", "9999");
        hfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        hfp.setattrvalue("Lagegenauigkeit", "5.0");
        hfp.setattrvalue("IstLagezuverlaessig", "ja");
        hfp.setattrvalue("Hoehengenauigkeit", "2.0");
        hfp.setattrvalue("IstHoehenzuverlaessig", "nein");

        Path outputPath = Files.createTempFile("dmav-hfp3-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, hfp),
                    Map.of("dmav", writer));

            assertThat(result.sourceRecordsRead()).isEqualTo(2);
            assertThat(result.targetsCreated()).isEqualTo(2);
            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("HFP3Nachfuehrung");
            assertThat(content).contains("HF001");
            assertThat(content).contains("HFP3");
            assertThat(content).contains("9999");
            assertThat(content).contains("true");
            assertThat(content).contains("false");
            assertThat(content).contains("0.05");
            assertThat(content).contains("0.02");
            assertThat(content).contains("2025-01-15T12:00:00");
            assertThat(result.summary()).contains("DETERMINISTIC_UUID");

            for (Diagnostic d : engineDiag.all()) {
                System.out.println("  Engine: [" + d.severity() + "] " + d.message());
            }
            assertThat(engineDiag.all()).isEmpty();
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void enumMapUnknownValueReportsWarning() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "HF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject hfp = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3", "10");
        hfp.setattrvalue("Entstehung", "1");
        hfp.setattrvalue("NBIdent", "HF001");
        hfp.setattrvalue("Nummer", "9999");
        hfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        hfp.setattrvalue("Lagegenauigkeit", "5.0");
        hfp.setattrvalue("IstLagezuverlaessig", "ja");
        hfp.setattrvalue("IstHoehenzuverlaessig", "UNBEKANNT");

        Path outputPath = Files.createTempFile("dmav-hfp3-unknown-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            engine.runTyped(plan, onceReaderFactory(nf, hfp), Map.of("dmav", writer));

            boolean hasWarning = engineDiag.all().stream()
                    .anyMatch(d -> d.message().contains("no mapping for source value")
                            && d.message().contains("UNBEKANNT"));
            assertThat(hasWarning).isTrue();
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void gueltigerEintragMissingDoesNotUseNowFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "HF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Kein Datum");

        Iom_jObject hfp = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3", "10");
        hfp.setattrvalue("Entstehung", "1");
        hfp.setattrvalue("NBIdent", "HF001");
        hfp.setattrvalue("Nummer", "9999");
        hfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        hfp.setattrvalue("Lagegenauigkeit", "5.0");
        hfp.setattrvalue("IstLagezuverlaessig", "ja");

        Path outputPath = Files.createTempFile("dmav-hfp3-now-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, hfp),
                    Map.of("dmav", writer));

            assertThat(result.errors()).isZero();
            String content = Files.readString(outputPath);
            assertThat(content).doesNotContain("GueltigerEintrag");
            assertThat(engineDiag.all()).isEmpty();
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void referenceEntstehungIsResolved() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "HF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Ref-Test");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject hfp = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3", "10");
        hfp.setattrvalue("Entstehung", "1");
        hfp.setattrvalue("NBIdent", "HF001");
        hfp.setattrvalue("Nummer", "9999");
        hfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        hfp.setattrvalue("Lagegenauigkeit", "5.0");
        hfp.setattrvalue("IstLagezuverlaessig", "ja");

        Path outputPath = Files.createTempFile("dmav-hfp3-ref-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, hfp),
                    Map.of("dmav", writer));

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            assertThat(content).contains("HFP3Nachfuehrung");
            assertThat(content).contains("HFP3");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void bagTextpositionForwardCreatesStructureInOutput() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        RulePlan hfp3Rule = plan.rules().stream()
                .filter(r -> r.ruleId().equals("hfp3"))
                .findFirst().orElseThrow();
        assertThat(hfp3Rule.bags()).hasSize(1);
        assertThat(hfp3Rule.bags().get(0).bagAttrName()).isEqualTo("Textposition");
        assertThat(hfp3Rule.bags().get(0).mode()).isEqualTo(BagPlan.BagMode.EMBED);

        Iom_jObject nf = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "HF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test-Nachfuehrung");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject hfp = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3", "10");
        hfp.setattrvalue("Entstehung", "1");
        hfp.setattrvalue("NBIdent", "HF001");
        hfp.setattrvalue("Nummer", "9999");
        hfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        hfp.setattrvalue("Lagegenauigkeit", "5.0");
        hfp.setattrvalue("IstLagezuverlaessig", "ja");
        hfp.setattrvalue("Hoehengenauigkeit", "2.0");
        hfp.setattrvalue("IstHoehenzuverlaessig", "nein");

        Iom_jObject pos1 = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Pos", "100");
        IomObject pos1Ref = pos1.addattrobj("HFP3Pos_von", Iom_jObject.REF);
        pos1Ref.setobjectrefoid("10");
        pos1.setattrvalue("Pos", "2600001.000");
        pos1.setattrvalue("Ori", "45.0");
        pos1.setattrvalue("HAli", "Center");
        pos1.setattrvalue("VAli", "Half");

        Iom_jObject pos2 = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Pos", "101");
        IomObject pos2Ref = pos2.addattrobj("HFP3Pos_von", Iom_jObject.REF);
        pos2Ref.setobjectrefoid("10");
        pos2.setattrvalue("Pos", "2600002.000");
        pos2.setattrvalue("Ori", "90.0");
        pos2.setattrvalue("HAli", "Right");
        pos2.setattrvalue("VAli", "Top");

        Path outputPath = Files.createTempFile("dmav-hfp3-bag-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan,
                    onceReaderFactory(nf, hfp, pos1, pos2),
                    Map.of("dmav", writer));

            assertThat(result.sourceRecordsRead()).isEqualTo(4);
            assertThat(result.targetsCreated()).isEqualTo(2);
            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("HFP3Nachfuehrung");
            assertThat(content).contains("HFP3");
            assertThat(content).contains("Textposition");
            assertThat(content).contains("Position");
            assertThat(content).contains("2600001.000");
            assertThat(content).contains("2600002.000");
            assertThat(content).contains("Orientierung");
            assertThat(content).contains("HReferenzpunkt");
            assertThat(content).contains("Center");
            assertThat(content).contains("Right");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void bagTextpositionZeroPositionsProducesNoEmptyStructures() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "HF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test-ZeroPos");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject hfp = new Iom_jObject("Dm01TestModel.Fixpunkte.HFP3", "10");
        hfp.setattrvalue("Entstehung", "1");
        hfp.setattrvalue("NBIdent", "HF001");
        hfp.setattrvalue("Nummer", "9999");
        hfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        hfp.setattrvalue("Lagegenauigkeit", "5.0");
        hfp.setattrvalue("IstLagezuverlaessig", "ja");

        Path outputPath = Files.createTempFile("dmav-hfp3-no-bag-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan,
                    onceReaderFactory(nf, hfp),
                    Map.of("dmav", writer));

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("HFP3");
            assertThat(content).doesNotContain("Textposition");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    private static IoxReader createMockReader(Iom_jObject... objects) {
        return new IoxReader() {
            private int index = 0;
            private final IoxEvent[] events;

            {
                var list = new ArrayList<IoxEvent>();
                list.add(new StartTransferEvent("test", null, null));
                list.add(new StartBasketEvent("Dm01TestModel.Fixpunkte", "b1"));
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
