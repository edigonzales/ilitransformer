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
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.geometry.IoxGeometryAdapter;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.InMemoryReferenceIndex;
import guru.interlis.transformer.state.InMemoryStateStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DmavToDm01BbIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MAPPING_FILE = "src/test/resources/mappings/dmav-to-dm01-bb-test.yaml";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;
    private static TransferDescription dm01TransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dmavResult = service.compileModel(MODELDIR + "dmav-bb-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DMAV BB model compilation errors:\n  " + errors);
        }
        dmavTs = new TypeSystemFacade(dmavResult.transferDescription());

        IliModelCompileResult dm01Result = service.compileModel(MODELDIR + "dm01-bb-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DM01 BB model compilation errors:\n  " + errors);
        }
        dm01TransferDescription = dm01Result.transferDescription();
        dm01Ts = new TypeSystemFacade(dm01TransferDescription);
    }

    @Test
    void compilesReverseBbMapping() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavBbTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01BbTestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);

        for (var d : plan.diagnostics().all()) {
            System.out.println("REV DIAG: [" + d.severity() + "] " + d.code() + ": " + d.message());
        }

        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(3);
        assertThat(plan.oidPlan().defaultStrategy().name()).isEqualTo("INTEGER");
    }

    @Test
    void transformsBodenbedeckungToBoflaeche() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavBbTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01BbTestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.BBNachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "DMAV-BB-Nachfuehrung");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T10:30:00+00:00");

        Iom_jObject bb = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Bodenbedeckung", "uuid-bb-1");
        bb.setattrvalue("Entstehung", "uuid-nf-1");
        bb.setattrvalue("Geometrie", "2600000.000 1200000.000");
        bb.setattrvalue("Qualitaetsstandard", "AV93");
        bb.setattrvalue("Bodenbedeckungsart", "Gebaeude");
        bb.setattrvalue("Fiktiv", "false");
        bb.setattrvalue("Objektstatus", "real");
        bb.setattrvalue("EGID", "42");

        // Objektnummer BAG with nested Textposition
        Iom_jObject onrTp = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Textposition", "tp-onr-1");
        onrTp.setattrvalue("Position", "2600010.000");
        onrTp.setattrvalue("Orientierung", "45.0");
        onrTp.setattrvalue("HReferenzpunkt", "Center");
        onrTp.setattrvalue("VReferenzpunkt", "Half");
        Iom_jObject onrBag = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Objektnummer", "onr-1");
        onrBag.setattrvalue("Nummer", "17");
        IomObject onrTpBag = onrBag.addattrobj("Textposition", "BAG");
        onrTpBag.addattrobj("Textposition", onrTp);
        IomObject onrContainer = bb.addattrobj("Objektnummer", "BAG");
        onrContainer.addattrobj("Objektnummer", onrBag);

        // Objektname BAG with nested Textposition
        Iom_jObject onTp = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Textposition", "tp-on-1");
        onTp.setattrvalue("Position", "2600020.000");
        onTp.setattrvalue("Orientierung", "90.0");
        onTp.setattrvalue("HReferenzpunkt", "Right");
        onTp.setattrvalue("VReferenzpunkt", "Top");
        Iom_jObject onName = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Objektname", "on-1");
        onName.setattrvalue("Name", "Testgebaeude");
        IomObject onTpBag = onName.addattrobj("Textposition", "BAG");
        onTpBag.addattrobj("Textposition", onTp);
        IomObject onContainer = bb.addattrobj("Objektname", "BAG");
        onContainer.addattrobj("Objektname", onName);

        // Symbolposition BAG
        Iom_jObject sym = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Textposition", "tp-sym-1");
        sym.setattrvalue("Position", "2600030.000");
        sym.setattrvalue("Orientierung", "180.0");
        IomObject symBag = bb.addattrobj("Symbolposition", "BAG");
        symBag.addattrobj("Textposition", sym);

        Path outputPath = Files.createTempFile("dm01-bb-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(),
                    new InMemoryStateStore(),
                    engineDiag,
                    new IoxGeometryAdapter(),
                    new DefaultOidGenerationService(),
                    new InMemoryReferenceIndex());
            TransformResult result = engine.runTyped(plan,
                    onceReaderFactory(nf, bb), Map.of("dm01", writer));

            for (var d : engineDiag.all()) {
                System.out.println("  Rev Engine: [" + d.severity() + "] " + d.code() + ": " + d.message());
            }

            boolean hasErrors = engineDiag.all().stream()
                    .anyMatch(d -> "ERROR".equals(d.severity().name()));
            boolean hasAmbiguousRefs = engineDiag.all().stream()
                    .anyMatch(d -> "ILITRF-RUN-REF-AMBIGUOUS".equals(d.code()));
            assertThat(hasErrors)
                    .as("Engine diagnostics: %s", engineDiag.all())
                    .isFalse();
            assertThat(hasAmbiguousRefs)
                    .as("Engine diagnostics: %s", engineDiag.all())
                    .isFalse();
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("BBNachfuehrung");
            assertThat(content).contains("NF001");
            assertThat(content).contains("BoFlaeche");
            assertThat(content).contains("Gebaeude");
            assertThat(content).contains("ETAB");
            assertThat(content).contains("GebaeudenummerPos");
            assertThat(content).contains("ObjektnamePos");

        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void transformsBodenbedeckungWithoutBags() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavBbTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01BbTestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.BBNachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "No-Bags-Reverse");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T00:00:00+00:00");

        Iom_jObject bb = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Bodenbedeckung", "uuid-bb-1");
        bb.setattrvalue("Entstehung", "uuid-nf-1");
        bb.setattrvalue("Geometrie", "2600000.000 1200000.000");
        bb.setattrvalue("Qualitaetsstandard", "AV93");
        bb.setattrvalue("Bodenbedeckungsart", "befestigt");
        bb.setattrvalue("Fiktiv", "false");
        bb.setattrvalue("Objektstatus", "real");
        bb.setattrvalue("EGID", "17");

        Path outputPath = Files.createTempFile("dm01-bb-no-bag-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan,
                    onceReaderFactory(nf, bb), Map.of("dm01", writer));

            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            assertThat(content).contains("BoFlaeche");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void transformsMesspunktToEinzelpunkt() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavBbTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01BbTestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.BBNachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Messpunkt-Test");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T00:00:00+00:00");

        Iom_jObject mp = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Messpunkt", "uuid-mp-1");
        mp.setattrvalue("Entstehung", "uuid-nf-1");
        mp.setattrvalue("Nummer", "MP001");
        mp.setattrvalue("Geometrie", "2600100.000 1200100.000");
        mp.setattrvalue("Lagegenauigkeit", "0.05");
        mp.setattrvalue("IstLagezuverlaessig", "true");
        mp.setattrvalue("IstExaktDefiniert", "true");

        Path outputPath = Files.createTempFile("dm01-bb-mp-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan,
                    onceReaderFactory(nf, mp), Map.of("dm01", writer));

            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            assertThat(content).contains("Einzelpunkt");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void integerOidIsGeneratedForDm01Target() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavBbTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01BbTestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.oidPlan().defaultStrategy().name()).isEqualTo("INTEGER");

        Iom_jObject nf = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.BBNachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "OID-Test-BB");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T00:00:00+00:00");

        Iom_jObject bb = new Iom_jObject("DmavBbTestModel.Bodenbedeckung.Bodenbedeckung", "uuid-bb-1");
        bb.setattrvalue("Entstehung", "uuid-nf-1");
        bb.setattrvalue("Geometrie", "2600000.000 1200000.000");
        bb.setattrvalue("Qualitaetsstandard", "AV93");
        bb.setattrvalue("Bodenbedeckungsart", "befestigt");
        bb.setattrvalue("Fiktiv", "false");
        bb.setattrvalue("Objektstatus", "real");
        bb.setattrvalue("EGID", "7");

        Path outputPath = Files.createTempFile("dm01-bb-oid-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine = new TransformationEngine(
                    new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan,
                    onceReaderFactory(nf, bb), Map.of("dm01", writer));

            assertThat(result.summary()).contains("INTEGER");
            String content = Files.readString(outputPath);
            assertThat(content).contains("OBJE 1");
            assertThat(content).contains("OBJE 2");
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
                list.add(new StartBasketEvent("DmavBbTestModel.Bodenbedeckung", "b1"));
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
