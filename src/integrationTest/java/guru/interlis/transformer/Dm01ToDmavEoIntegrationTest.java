package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import guru.interlis.transformer.app.IlivalidatorRunner;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Dm01ToDmavEoIntegrationTest {

    private static final String MODELDIR =
            "src/test/data/av/models/;https://models.interlis.ch;https://models.geo.admin.ch;https://models.kgk-cgc.ch";
    private static final String MAPPING_FILE = "profiles/dm01-to-dmav/1.1/eo.yaml";
    private static final String SOURCE_MODEL = "DM01AVCH24LV95D";
    private static final String TARGET_MODEL = "DMAV_Einzelobjekte_V1_1";
    private static final String SOURCE_BASKET = "DM01AVCH24LV95D.Einzelobjekte";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;
    private static TransferDescription dmavTransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dm01Result = service.compileModel(SOURCE_MODEL, MODELDIR);
        if (dm01Result.hasErrors()) {
            fail("DM01 compile errors:\n  " + formatDiagnostics(dm01Result));
        }
        dm01Ts = new TypeSystemFacade(dm01Result.transferDescription());

        IliModelCompileResult dmavResult = service.compileModel(TARGET_MODEL, MODELDIR);
        if (dmavResult.hasErrors()) {
            fail("DMAV compile errors:\n  " + formatDiagnostics(dmavResult));
        }
        dmavTransferDescription = dmavResult.transferDescription();
        dmavTs = new TypeSystemFacade(dmavTransferDescription);
    }

    @Test
    void compilesEoMapping() throws Exception {
        TransformPlan plan = compilePlan();

        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(7);
    }

    @Test
    void transformsGueltigAndProjectedEinzelobjekteAndMesspunkte() throws Exception {
        TransformPlan plan = compilePlan();
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nfGueltig = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.EONachfuehrung", "1");
        nfGueltig.setattrvalue("NBIdent", "NB001");
        nfGueltig.setattrvalue("Identifikator", "ID-REAL");
        nfGueltig.setattrvalue("Beschreibung", "Gueltige Nachfuehrung");
        nfGueltig.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject nfProjektiert = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.EONachfuehrung", "2");
        nfProjektiert.setattrvalue("NBIdent", "NB001");
        nfProjektiert.setattrvalue("Identifikator", "ID-PROJ");
        nfProjektiert.setattrvalue("Beschreibung", "Projektierte Nachfuehrung");
        nfProjektiert.setattrvalue("Gueltigkeit", "projektiert");
        nfProjektiert.setattrvalue("GueltigerEintrag", "2025-02-20");

        Iom_jObject eoGueltig = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Einzelobjekt", "10");
        eoGueltig.addattrobj("Entstehung", Iom_jObject.REF).setobjectrefoid("1");
        eoGueltig.setattrvalue("Qualitaet", "AV93");
        eoGueltig.setattrvalue("Art", "Mauer");

        Iom_jObject eoProjektiert = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Einzelobjekt", "11");
        eoProjektiert.addattrobj("Entstehung", Iom_jObject.REF).setobjectrefoid("2");
        eoProjektiert.setattrvalue("Qualitaet", "AV93");
        eoProjektiert.setattrvalue("Art", "Mauer");

        Iom_jObject punktGueltig = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Punktelement", "100");
        punktGueltig.addattrobj("Punktelement_von", Iom_jObject.REF).setobjectrefoid("10");
        punktGueltig.addattrobj("Geometrie", coord("2600000.0", "1200000.0"));
        punktGueltig.setattrvalue("Ori", "15.0");

        Iom_jObject punktProjektiert = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Punktelement", "101");
        punktProjektiert.addattrobj("Punktelement_von", Iom_jObject.REF).setobjectrefoid("11");
        punktProjektiert.addattrobj("Geometrie", coord("2600010.0", "1200010.0"));
        punktProjektiert.setattrvalue("Ori", "25.0");

        Iom_jObject nummerGueltig = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Objektnummer", "200");
        nummerGueltig.addattrobj("Objektnummer_von", Iom_jObject.REF).setobjectrefoid("10");
        nummerGueltig.setattrvalue("Nummer", "EO-REAL");
        nummerGueltig.setattrvalue("GWR_EGID", "42");

        Iom_jObject nummerProjektiert = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Objektnummer", "201");
        nummerProjektiert.addattrobj("Objektnummer_von", Iom_jObject.REF).setobjectrefoid("11");
        nummerProjektiert.setattrvalue("Nummer", "EO-PROJ");
        nummerProjektiert.setattrvalue("GWR_EGID", "77");

        Iom_jObject messpunktOhneEntstehung =
                messpunkt("20", null, "EP-NULL", "2600020.0", "1200020.0", "5", "ja", "Ja");
        Iom_jObject messpunktGueltig = messpunkt("21", "1", "EP-REAL", "2600030.0", "1200030.0", "6", "ja", "Nein");
        Iom_jObject messpunktProjektiert = messpunkt("22", "2", "EP-PROJ", "2600040.0", "1200040.0", "7", "nein", "Ja");

        Path outputPath = Files.createTempFile("dmav-eo-", ".xtf");
        Path validationLog = Files.createTempFile("dmav-eo-", ".log");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(
                    plan,
                    onceReaderFactory(
                            nfGueltig,
                            nfProjektiert,
                            eoGueltig,
                            eoProjektiert,
                            punktGueltig,
                            punktProjektiert,
                            nummerGueltig,
                            nummerProjektiert,
                            messpunktOhneEntstehung,
                            messpunktGueltig,
                            messpunktProjektiert),
                    Map.of("dmav", writer));

            assertThat(result.sourceRecordsRead()).isEqualTo(11);
            assertThat(result.targetsWritten()).isGreaterThanOrEqualTo(7);
            assertThat(result.errors()).isZero();
            assertThat(engineDiag.errors()).as(engineDiag.all().toString()).isZero();
            assertThat(engineDiag.all().stream()
                            .map(d -> d.code() == null ? "" : d.code())
                            .anyMatch(code ->
                                    code.contains("RUN_REF_MISSING_MANDATORY") || code.contains("RUN_REF_UNRESOLVED")))
                    .isFalse();

            IlivalidatorRunner.ValidationResult validation =
                    IlivalidatorRunner.validate(outputPath, List.of(MODELDIR), TARGET_MODEL, validationLog);
            assertThat(validation.success()).as(validation.log()).isTrue();

            List<IomObject> objects = readAllObjects(outputPath);
            List<String> objectTags =
                    objects.stream().map(IomObject::getobjecttag).toList();
            List<IomObject> nachfuehrungen = objects.stream()
                    .filter(obj -> obj.getobjecttag().endsWith(".EONachfuehrung"))
                    .toList();
            List<IomObject> einzelobjekte = objects.stream()
                    .filter(obj -> obj.getobjecttag().endsWith(".Einzelobjekt"))
                    .toList();
            List<IomObject> messpunkte = objects.stream()
                    .filter(obj -> obj.getobjecttag().endsWith(".Messpunkt"))
                    .toList();
            assertThat(nachfuehrungen).as("object tags: %s", objectTags).hasSize(2);
            assertThat(einzelobjekte).as("object tags: %s", objectTags).hasSize(2);
            assertThat(messpunkte).as("object tags: %s", objectTags).hasSize(3);

            Map<String, String> nachfuehrungOidByIdentifikator = objects.stream()
                    .filter(obj -> obj.getobjecttag().endsWith(".EONachfuehrung"))
                    .collect(Collectors.toMap(obj -> obj.getattrvalue("Identifikator"), IomObject::getobjectoid));

            Map<String, IomObject> einzelobjektByEgid = objects.stream()
                    .filter(obj -> obj.getobjecttag().endsWith(".Einzelobjekt"))
                    .collect(Collectors.toMap(obj -> obj.getattrvalue("EGID"), Function.identity()));

            IomObject realEinzelobjekt = einzelobjektByEgid.get("42");
            assertThat(realEinzelobjekt.getattrvalue("Objektstatus")).isEqualTo("real");
            assertThat(realEinzelobjekt.getattrobj("Entstehung", 0).getobjectrefoid())
                    .isEqualTo(nachfuehrungOidByIdentifikator.get("ID-REAL"));

            IomObject projEinzelobjekt = einzelobjektByEgid.get("77");
            assertThat(projEinzelobjekt.getattrvalue("Objektstatus")).isEqualTo("projektiert");
            assertThat(projEinzelobjekt.getattrobj("Entstehung", 0).getobjectrefoid())
                    .isEqualTo(nachfuehrungOidByIdentifikator.get("ID-PROJ"));

            Map<String, IomObject> messpunktByNummer = objects.stream()
                    .filter(obj -> obj.getobjecttag().endsWith(".Messpunkt"))
                    .collect(Collectors.toMap(obj -> obj.getattrvalue("Nummer"), Function.identity()));

            assertThat(messpunktByNummer.get("EP-NULL").getattrvaluecount("Entstehung"))
                    .isZero();
            assertThat(messpunktByNummer
                            .get("EP-REAL")
                            .getattrobj("Entstehung", 0)
                            .getobjectrefoid())
                    .isEqualTo(nachfuehrungOidByIdentifikator.get("ID-REAL"));
            assertThat(messpunktByNummer
                            .get("EP-PROJ")
                            .getattrobj("Entstehung", 0)
                            .getobjectrefoid())
                    .isEqualTo(nachfuehrungOidByIdentifikator.get("ID-PROJ"));
        } finally {
            Files.deleteIfExists(outputPath);
            Files.deleteIfExists(validationLog);
        }
    }

    private static TransformPlan compilePlan() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);
        return new MappingCompiler().compileTyped(config, Map.of(SOURCE_MODEL, dm01Ts), Map.of(TARGET_MODEL, dmavTs));
    }

    private static Iom_jObject coord(String c1, String c2) {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", c1);
        coord.setattrvalue("C2", c2);
        return coord;
    }

    private static Iom_jObject messpunkt(
            String oid,
            String entstehungOid,
            String identifikator,
            String c1,
            String c2,
            String lageGen,
            String lageZuv,
            String exaktDefiniert) {
        Iom_jObject messpunkt = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Einzelpunkt", oid);
        if (entstehungOid != null) {
            messpunkt.addattrobj("Entstehung", Iom_jObject.REF).setobjectrefoid(entstehungOid);
        }
        messpunkt.setattrvalue("Identifikator", identifikator);
        messpunkt.addattrobj("Geometrie", coord(c1, c2));
        messpunkt.setattrvalue("LageGen", lageGen);
        messpunkt.setattrvalue("LageZuv", lageZuv);
        messpunkt.setattrvalue("ExaktDefiniert", exaktDefiniert);
        return messpunkt;
    }

    private static List<IomObject> readAllObjects(Path path) throws Exception {
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        List<IomObject> objects = new ArrayList<>();
        IoxReader reader = ioFactory.createReader(path, dmavTransferDescription);
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    objects.add(objectEvent.getIomObject());
                } else if (event instanceof ch.interlis.iox.EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private static IoxReader createMockReader(Iom_jObject... objects) {
        IoxEvent[] events = new IoxEvent[objects.length + 4];
        int i = 0;
        events[i++] = new StartTransferEvent("eo-test", null, null);
        events[i++] = new StartBasketEvent(SOURCE_BASKET, "b1");
        for (Iom_jObject object : objects) {
            events[i++] = new ObjectEvent(object);
        }
        events[i++] = new EndBasketEvent();
        events[i] = new EndTransferEvent();

        return new IoxReader() {
            private int index = 0;

            @Override
            public IoxEvent read() {
                if (index < events.length) {
                    return events[index++];
                }
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

    private static Function<String, IoxReader> onceReaderFactory(Iom_jObject... objects) {
        return new Function<>() {
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
                        public void setFactory(ch.interlis.iox.IoxFactoryCollection factory) {}
                    };
                }
                used = true;
                return createMockReader(objects);
            }
        };
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((left, right) -> left + "\n  " + right)
                .orElse("<none>");
    }
}
