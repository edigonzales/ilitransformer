package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Dm01ToDmavBbIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MAPPING_FILE = "src/test/resources/mappings/dm01-to-dmav-bb-test.yaml";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;
    private static TransferDescription dmavTransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dm01Result = service.compileModel(MODELDIR + "dm01-bb-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DM01 BB model compilation errors:\n  " + errors);
        }
        dm01Ts = new TypeSystemFacade(dm01Result.transferDescription());

        IliModelCompileResult dmavResult = service.compileModel(MODELDIR + "dmav-bb-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DMAV BB model compilation errors:\n  " + errors);
        }
        dmavTransferDescription = dmavResult.transferDescription();
        dmavTs = new TypeSystemFacade(dmavTransferDescription);
    }

    @Test
    void compilesBbMapping() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01BbTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavBbTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);

        for (var d : plan.diagnostics().all()) {
            System.out.println("DIAG: [" + d.severity() + "] " + d.code() + ": " + d.message());
        }

        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.diagnostics().warnings()).isZero();
        assertThat(plan.rules()).hasSize(5);
    }

    @Test
    void transformsBbWithFirstLevelBags() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01BbTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavBbTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        // BBNachfuehrung
        Iom_jObject nf = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BBNachfuehrung", "1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test-BB-Nachfuehrung");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        // BoFlaeche
        Iom_jObject bb = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BoFlaeche", "10");
        bb.setattrvalue("Entstehung", "1");
        bb.setattrvalue("Geometrie", "2600000.000 1200000.000");
        bb.setattrvalue("Qualitaet", "AV93");
        bb.setattrvalue("Art", "Gebaeude");

        // Gebaeudenummer (first-level bag child of BoFlaeche)
        Iom_jObject gn = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.Gebaeudenummer", "100");
        IomObject gnRef = gn.addattrobj("Gebaeudenummer_von", Iom_jObject.REF);
        gnRef.setobjectrefoid("10");
        gn.setattrvalue("Nummer", "42");

        // GebaeudenummerPos (nested bag child of Gebaeudenummer)
        Iom_jObject gnp = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.GebaeudenummerPos", "200");
        IomObject gnpRef = gnp.addattrobj("GebaeudenummerPos_von", Iom_jObject.REF);
        gnpRef.setobjectrefoid("100");
        gnp.setattrvalue("Pos", "2600001.000");
        gnp.setattrvalue("Ori", "45.0");
        gnp.setattrvalue("HAli", "Center");
        gnp.setattrvalue("VAli", "Half");

        // Objektname (first-level bag child of BoFlaeche)
        Iom_jObject on = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.Objektname", "300");
        IomObject onRef = on.addattrobj("Objektname_von", Iom_jObject.REF);
        onRef.setobjectrefoid("10");
        on.setattrvalue("Name", "Testname");

        // ObjektnamePos (nested bag child of Objektname)
        Iom_jObject onp = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.ObjektnamePos", "400");
        IomObject onpRef = onp.addattrobj("ObjektnamePos_von", Iom_jObject.REF);
        onpRef.setobjectrefoid("300");
        onp.setattrvalue("Pos", "2600002.000");
        onp.setattrvalue("Ori", "90.0");
        onp.setattrvalue("HAli", "Right");
        onp.setattrvalue("VAli", "Top");

        // BoFlaecheSymbol (first-level bag child of BoFlaeche)
        Iom_jObject sym = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BoFlaecheSymbol", "500");
        IomObject symRef = sym.addattrobj("BoFlaecheSymbol_von", Iom_jObject.REF);
        symRef.setobjectrefoid("10");
        sym.setattrvalue("Pos", "2600003.000");
        sym.setattrvalue("Ori", "180.0");

        Path outputPath = Files.createTempFile("dmav-bb-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result =
                    engine.runTyped(plan, onceReaderFactory(nf, bb, gn, gnp, on, onp, sym), Map.of("dmav", writer));

            for (Diagnostic d : engineDiag.all()) {
                System.out.println("  Engine: [" + d.severity() + "] " + d.code() + ": " + d.message());
            }
            assertThat(engineDiag.all()).isEmpty();

            assertThat(result.sourceRecordsRead()).isEqualTo(7);
            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("BBNachfuehrung");
            assertThat(content).contains("NF001");
            assertThat(content).contains("Bodenbedeckung");
            assertThat(content).contains("Gebaeude");
            assertThat(content).contains("false");
            assertThat(content).contains("real");
            assertThat(content).contains("1");

            // First-level bag Objektnummer created
            assertThat(content).contains("Objektnummer");
            assertThat(content).contains("42");
            // First-level bag Objektname created
            assertThat(content).contains("Objektname");
            assertThat(content).contains("Testname");
            // First-level bag Symbolposition with Textposition
            assertThat(content).contains("Symbolposition");
            assertThat(content).contains("Textposition");
            assertThat(content).contains("2600003.000");
            assertThat(content).contains("180.0");

            // Nested bag Textposition inside Objektnummer
            assertThat(content).contains("2600001.000");
            assertThat(content).contains("45.0");
            assertThat(content).contains("HReferenzpunkt");
            assertThat(content).contains("Center");

            // Nested bag Textposition inside Objektname
            assertThat(content).contains("2600002.000");
            assertThat(content).contains("90.0");
            assertThat(content).contains("Right");

        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void transformsProjectedBbToProjectedDmavStatus() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01BbTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavBbTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().all()).isEmpty();

        Iom_jObject nf = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BBNachfuehrung", "1");
        nf.setattrvalue("NBIdent", "NF002");
        nf.setattrvalue("Identifikator", "ID002");
        nf.setattrvalue("Beschreibung", "Projected-BB");
        nf.setattrvalue("Gueltigkeit", "projektiert");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject bb = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.ProjBoFlaeche", "10");
        bb.setattrvalue("Entstehung", "1");
        bb.setattrvalue("Geometrie", "2600000.000 1200000.000");
        bb.setattrvalue("Qualitaet", "AV93");
        bb.setattrvalue("Art", "Gebaeude");

        Iom_jObject gn = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.ProjGebaeudenummer", "100");
        gn.addattrobj("ProjGebaeudenummer_von", Iom_jObject.REF).setobjectrefoid("10");
        gn.setattrvalue("Nummer", "77");

        Path outputPath = Files.createTempFile("dmav-bb-proj-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, bb, gn), Map.of("dmav", writer));

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);
            assertThat(engineDiag.all()).isEmpty();

            String content = Files.readString(outputPath);
            assertThat(content).contains("Bodenbedeckung");
            assertThat(content).contains("projektiert");
            assertThat(content).contains("false");
            assertThat(content).contains("77");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void referenceBodenbedeckungEntstehungIsResolved() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01BbTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavBbTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BBNachfuehrung", "1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Ref-Test-BB");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject bb = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BoFlaeche", "10");
        bb.setattrvalue("Entstehung", "1");
        bb.setattrvalue("Geometrie", "2600000.000 1200000.000");
        bb.setattrvalue("Qualitaet", "AV93");
        bb.setattrvalue("Art", "befestigt");

        Path outputPath = Files.createTempFile("dmav-bb-ref-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, bb), Map.of("dmav", writer));

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            assertThat(content).contains("BBNachfuehrung");
            assertThat(content).contains("Bodenbedeckung");
            // Verify Entstehung ref was resolved
            assertThat(content).contains("Entstehung");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void einzelpunktTransformsToMesspunkt() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01BbTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavBbTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BBNachfuehrung", "1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Einzelpunkt-Test");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject ep = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.Einzelpunkt", "20");
        ep.setattrvalue("Entstehung", "1");
        ep.setattrvalue("Identifikator", "EP001");
        ep.setattrvalue("Geometrie", "2600100.000 1200100.000");
        ep.setattrvalue("LageGen", "5.0");
        ep.setattrvalue("LageZuv", "ja");
        ep.setattrvalue("ExaktDefiniert", "Ja");

        Path outputPath = Files.createTempFile("dmav-bb-ep-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, ep), Map.of("dmav", writer));

            for (Diagnostic d : engineDiag.all()) {
                System.out.println("  EP Engine: [" + d.severity() + "] " + d.code() + ": " + d.message());
            }
            assertThat(engineDiag.all()).isEmpty();

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            assertThat(content).contains("Messpunkt");
            assertThat(content).contains("EP001");
            assertThat(content).contains("0.05");
            assertThat(content).contains("true");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void boflaecheWithoutBagsProducesNoEmptyStructures() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01BbTestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavBbTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BBNachfuehrung", "1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "No-Bags-Test");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject bb = new Iom_jObject("Dm01BbTestModel.Bodenbedeckung.BoFlaeche", "10");
        bb.setattrvalue("Entstehung", "1");
        bb.setattrvalue("Geometrie", "2600000.000 1200000.000");
        bb.setattrvalue("Qualitaet", "AV93");
        bb.setattrvalue("Art", "humusiert");

        Path outputPath = Files.createTempFile("dmav-bb-no-bag-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, bb), Map.of("dmav", writer));

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("Bodenbedeckung");
            assertThat(content).contains("humusiert");
            assertThat(content).doesNotContain("Objektnummer");
            assertThat(content).doesNotContain("Objektname");
            assertThat(content).doesNotContain("Symbolposition");
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
                list.add(new StartBasketEvent("Dm01BbTestModel.Bodenbedeckung", "b1"));
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
            public ch.interlis.iox.IoxFactoryCollection getFactory() {
                return null;
            }

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
                        @Override
                        public IoxEvent read() {
                            return null;
                        }

                        @Override
                        public void close() {}

                        @Override
                        public IomObject createIomObject(String tag, String oid) {
                            return new Iom_jObject(tag, oid);
                        }

                        @Override
                        public ch.interlis.iox.IoxFactoryCollection getFactory() {
                            return null;
                        }

                        @Override
                        public void setFactory(ch.interlis.iox.IoxFactoryCollection f) {}
                    };
                }
                used = true;
                return createMockReader(objects);
            }
        };
    }
}
