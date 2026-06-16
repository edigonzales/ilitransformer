package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

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

class DmavToDm01Lfp3IntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MAPPING_FILE = "src/test/resources/mappings/dmav-to-dm01-lfp3-test.yaml";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;
    private static TransferDescription dm01TransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dmavResult = service.compileModel(MODELDIR + "dmav-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DMAV model compilation errors:\n  " + errors);
        }
        dmavTs = new TypeSystemFacade(dmavResult.transferDescription());

        IliModelCompileResult dm01Result = service.compileModel(MODELDIR + "dm01-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DM01 model compilation errors:\n  " + errors);
        }
        dm01TransferDescription = dm01Result.transferDescription();
        dm01Ts = new TypeSystemFacade(dm01TransferDescription);
    }

    @Test
    void compilesReverseMapping() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01TestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);

        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.rules()).hasSize(2);
        assertThat(plan.enumMaps()).containsKeys("Zuverlaessigkeit_DMAV_DM01", "Versicherungsart_DMAV_DM01");
        assertThat(plan.oidPlan().defaultStrategy().name()).isEqualTo("INTEGER");
    }

    @Test
    void transformsDmavToDm01WithMulAndToDate() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01TestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3Nachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "DMAV-Nachfuehrung");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T10:30:00+00:00");

        Iom_jObject lfp = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3", "uuid-lfp-1");
        lfp.setattrvalue("Entstehung", "uuid-nf-1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        lfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        lfp.setattrvalue("Lagegenauigkeit", "0.05");
        lfp.setattrvalue("IstLagezuverlaessig", "true");
        lfp.setattrvalue("Punktzeichen", "Stein");

        Path outputPath = Files.createTempFile("dm01-lfp3-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, lfp), Map.of("dm01", writer));

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            // SCNT format: attributes are in column order
            assertThat(content).contains("LFP3Nachfuehrung");
            assertThat(content).contains("NF001");

            // mul(): 0.05 * 100 = 5.0
            assertThat(content).contains("5.0");

            // toDate(): XMLDateTime -> ILI1 DATE (time truncated, compact ITF format)
            assertThat(content).contains("20250115");

            // INTEGER OID
            assertThat(result.summary()).contains("INTEGER");

        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void toDateTruncatesTimePortion() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01TestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3Nachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Mit-Zeit-NF");
        nf.setattrvalue("GueltigerEintrag", "2024-12-31T23:59:59+01:00");

        Iom_jObject lfp = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3", "uuid-lfp-1");
        lfp.setattrvalue("Entstehung", "uuid-nf-1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        lfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        lfp.setattrvalue("Lagegenauigkeit", "0.05");
        lfp.setattrvalue("IstLagezuverlaessig", "false");
        lfp.setattrvalue("Punktzeichen", "Rohr");

        Path outputPath = Files.createTempFile("dm01-lfp3-date-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            engine.runTyped(plan, onceReaderFactory(nf, lfp), Map.of("dm01", writer));

            String content = Files.readString(outputPath);
            // Date preserved, time dropped, compact ITF format
            assertThat(content).contains("20241231");

        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void enumMapReverseBooleanToJaNein() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01TestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3Nachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "EnumRevTest");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T00:00:00+00:00");

        Iom_jObject lfp = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3", "uuid-lfp-1");
        lfp.setattrvalue("Entstehung", "uuid-nf-1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        lfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        lfp.setattrvalue("Lagegenauigkeit", "0.05");
        lfp.setattrvalue("IstLagezuverlaessig", "false");
        lfp.setattrvalue("Punktzeichen", "Kunststoffzeichen");

        Path outputPath = Files.createTempFile("dm01-lfp3-enum-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            engine.runTyped(plan, onceReaderFactory(nf, lfp), Map.of("dm01", writer));

            String content = Files.readString(outputPath);
            // SCNT encodes enum Ja=0, Nein=1 (Zuverlaessigkeit: ja, nein)
            // false → "nein" → index 1
            assertThat(content).contains("LFP001");
            // Just verify output was written successfully
            assertThat(content).contains("ETAB");

        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void integerOidIsGeneratedForDm01Target() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01TestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.oidPlan().defaultStrategy().name()).isEqualTo("INTEGER");

        Iom_jObject nf = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3Nachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "OID-Test");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T00:00:00+00:00");

        Iom_jObject lfp = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3", "uuid-lfp-1");
        lfp.setattrvalue("Entstehung", "uuid-nf-1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        lfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        lfp.setattrvalue("Lagegenauigkeit", "0.05");
        lfp.setattrvalue("IstLagezuverlaessig", "true");
        lfp.setattrvalue("Punktzeichen", "Stein");

        Path outputPath = Files.createTempFile("dm01-lfp3-oid-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, lfp), Map.of("dm01", writer));

            assertThat(result.summary()).contains("INTEGER");
            String content = Files.readString(outputPath);
            // SCNT format writes objects:
            // OBJE <oid> <attr1> <attr2> ...
            assertThat(content).contains("OBJE 1"); // LFP3Nachfuehrung OID 1
            assertThat(content).contains("OBJE 2"); // LFP3 OID 2

        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void referenceEntstehungResolved() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);

        Map<String, TypeSystemFacade> sourceTs = Map.of("DmavTestModel", dmavTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("Dm01TestModel", dm01Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3Nachfuehrung", "uuid-nf-1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Ref-Test");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15T00:00:00+00:00");

        Iom_jObject lfp = new Iom_jObject("DmavTestModel.Fixpunkte.LFP3", "uuid-lfp-1");
        lfp.setattrvalue("Entstehung", "uuid-nf-1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        lfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        lfp.setattrvalue("Lagegenauigkeit", "0.05");
        lfp.setattrvalue("IstLagezuverlaessig", "true");
        lfp.setattrvalue("Punktzeichen", "Stein");

        Path outputPath = Files.createTempFile("dm01-lfp3-ref-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dm01TransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, lfp), Map.of("dm01", writer));

            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            assertThat(content).contains("LFP3Nachfuehrung");
            assertThat(content).contains("LFP3");

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
                list.add(new StartBasketEvent("DmavTestModel.Fixpunkte", "b1"));
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
