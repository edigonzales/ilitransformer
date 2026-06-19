package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.PreparedJob;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.support.TestGeometries;

import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class GebaeudeadressenProfileRegressionTest {

    private static final Path PROFILE = Dm01DmavFixtures.GA.dm01ToDmavProfile();
    private static final String ILI_NS = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    private static final String GA_NS = "http://www.interlis.ch/xtf/2.4/DMAV_Gebaeudeadressen_V1_1";

    @TempDir
    Path tempDir;

    @Test
    void forwardProfileKeepsDistinctLokalisationenAndMapsBenanntesGebiet() throws Exception {
        PreparedJob prepared = prepareProfile();
        TransformPlan plan = prepared.plan();
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = tempDir.resolve("gebaeudeadressen-regression.xtf");
        DiagnosticCollector runtimeDiagnostics = new DiagnosticCollector();

        TransformResult result;
        IoxWriter writer = new InterlisIoFactory()
                .createWriter(outputPath, plan.outputsById().get("dmav").transferDescription(), runtimeDiagnostics);
        try {
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), runtimeDiagnostics);
            result = engine.runTyped(
                    plan, inputId -> TestMockReaders.mockReader(sourceObjects()), Map.of("dmav", writer));
        } finally {
            try {
                writer.close();
            } catch (Exception ignored) {
                // TransformationEngine closes writers on the normal path.
            }
        }

        assertThat(result.targetsCreated()).isEqualTo(5);
        assertThat(result.errors()).isEqualTo(0);
        assertThat(errorCodes(runtimeDiagnostics))
                .doesNotContain(
                        DiagnosticCode.RUN_DUPLICATE_TARGET_OID,
                        DiagnosticCode.RUN_REF_CARDINALITY,
                        DiagnosticCode.RUN_REF_MISSING_MANDATORY);

        Document document = parse(outputPath);
        List<Element> lokalisationen = topLevelObjects(document, "Lokalisation");
        assertThat(lokalisationen).hasSize(3);
        assertThat(uniqueTids(lokalisationen)).hasSize(3);

        List<Element> strassen = lokalisationen.stream()
                .filter(element -> "Strasse".equals(directChildText(element, "Lokalisationsart")))
                .toList();
        List<Element> benannteGebiete = lokalisationen.stream()
                .filter(element -> "BenanntesGebiet".equals(directChildText(element, "Lokalisationsart")))
                .toList();

        assertThat(strassen).hasSize(2);
        assertThat(benannteGebiete).hasSize(1);

        for (Element lokalisation : lokalisationen) {
            assertThat(directChildren(lokalisation, "Entstehung")).hasSize(1);
            assertThat(directChildren(lokalisation, "LokalisationName")).hasSize(1);
        }
        for (Element strasse : strassen) {
            assertThat(directChildren(strasse, "Strassenstueck")).hasSize(1);
            assertThat(directChildren(strasse, "BenanntesGebiet")).isEmpty();
        }
        for (Element benanntesGebiet : benannteGebiete) {
            assertThat(directChildren(benanntesGebiet, "BenanntesGebiet")).hasSize(1);
            assertThat(directChildren(benanntesGebiet, "Strassenstueck")).isEmpty();
        }

        List<Element> eingaenge = topLevelObjects(document, "Gebaeudeeingang");
        assertThat(eingaenge).hasSize(1);
        List<Element> lokalisationRefs = directChildren(eingaenge.get(0), "Lokalisation");
        assertThat(lokalisationRefs).hasSize(1);
        assertThat(lokalisationRefs.get(0).getAttributeNS(ILI_NS, "ref")).isNotBlank();
    }

    private PreparedJob prepareProfile() throws Exception {
        Path materializedProfile = tempDir.resolve("gebaeudeadressen-regression.yaml");
        String yaml = Files.readString(PROFILE, StandardCharsets.UTF_8).replace(
                """
                  modeldir:
                    - "https://models.geo.admin.ch/"
                    - "models/"
                """,
                """
                  modeldir:
                    - "src/test/data/av/models"
                    - "https://models.interlis.ch"
                """);
        Files.writeString(materializedProfile, yaml, StandardCharsets.UTF_8);
        return new JobRunner().prepare(materializedProfile, new RunOptions(List.of()));
    }

    private Iom_jObject[] sourceObjects() {
        Iom_jObject nf = new Iom_jObject("DM01AVCH24LV95D.Gebaeudeadressen.GEBNachfuehrung", "nf-1");
        nf.setattrvalue("NBIdent", "GA_NB");
        nf.setattrvalue("Identifikator", "GA_ID001");
        nf.setattrvalue("Beschreibung", "GaProfileRegression");
        nf.setattrvalue("Gueltigkeit", "gueltig");
        nf.setattrvalue("GueltigerEintrag", "2025-03-15");

        Iom_jObject lokStrasse1 = lokalisation("lok-str-1", "nf-1", "aufsteigend", "101", "Strasse");
        Iom_jObject lokStrasse2 = lokalisation("lok-str-2", "nf-1", "aufsteigend", "102", "Strasse");
        Iom_jObject lokGebiet = lokalisation("lok-bg-1", "nf-1", "beliebig", "201", "BenanntesGebiet");

        Iom_jObject lnStrasse1 = lokalisationsName("ln-1", "lok-str-1", "Alphaweg");
        Iom_jObject lnStrasse2 = lokalisationsName("ln-2", "lok-str-2", "Betagasse");
        Iom_jObject lnGebiet = lokalisationsName("ln-3", "lok-bg-1", "Hinterfeld");

        Iom_jObject ss1 = strassenstueck("ss-1", "lok-str-1", 2600000.0, 1200000.0);
        Iom_jObject ss2 = strassenstueck("ss-2", "lok-str-2", 2600200.0, 1200200.0);
        Iom_jObject bg1 = benanntesGebiet("bg-1", "lok-bg-1");

        Iom_jObject eingang = new Iom_jObject("DM01AVCH24LV95D.Gebaeudeadressen.Gebaeudeeingang", "ge-1");
        eingang.setattrvalue("Entstehung", "nf-1");
        eingang.setattrvalue("Gebaeudeeingang_von", "lok-str-1");
        eingang.setattrvalue("Status", "real");
        eingang.setattrvalue("InAenderung", "nein");
        eingang.setattrvalue("AttributeProvisorisch", "nein");
        eingang.setattrvalue("IstOffizielleBezeichnung", "ja");
        eingang.addattrobj("Lage", TestGeometries.coord(2600050.0, 1200020.0));
        eingang.setattrvalue("Hausnummer", "10");
        eingang.setattrvalue("Im_Gebaeude", "BB");

        return new Iom_jObject[] {
            nf, lokStrasse1, lokStrasse2, lokGebiet, lnStrasse1, lnStrasse2, lnGebiet, ss1, ss2, bg1, eingang
        };
    }

    private Iom_jObject lokalisation(
            String oid, String entstehungOid, String nummerierungsprinzip, String nummer, String art) {
        Iom_jObject lokalisation = new Iom_jObject("DM01AVCH24LV95D.Gebaeudeadressen.Lokalisation", oid);
        lokalisation.setattrvalue("Entstehung", entstehungOid);
        lokalisation.setattrvalue("Nummerierungsprinzip", nummerierungsprinzip);
        lokalisation.setattrvalue("LokalisationNummer", nummer);
        lokalisation.setattrvalue("AttributeProvisorisch", "nein");
        lokalisation.setattrvalue("IstOffizielleBezeichnung", "ja");
        lokalisation.setattrvalue("Status", "real");
        lokalisation.setattrvalue("InAenderung", "nein");
        lokalisation.setattrvalue("Art", art);
        return lokalisation;
    }

    private Iom_jObject lokalisationsName(String oid, String lokalisationOid, String text) {
        Iom_jObject name = new Iom_jObject("DM01AVCH24LV95D.Gebaeudeadressen.LokalisationsName", oid);
        name.setattrvalue("Benannte", lokalisationOid);
        name.setattrvalue("Text", text);
        name.setattrvalue("Sprache", "de");
        return name;
    }

    private Iom_jObject strassenstueck(String oid, String lokalisationOid, double x, double y) {
        Iom_jObject strassenstueck = new Iom_jObject("DM01AVCH24LV95D.Gebaeudeadressen.Strassenstueck", oid);
        strassenstueck.setattrvalue("Strassenstueck_von", lokalisationOid);
        strassenstueck.addattrobj(
                "Geometrie",
                TestGeometries.polyline(
                        TestGeometries.coord(x, y),
                        TestGeometries.coord(x + 40.0, y),
                        TestGeometries.coord(x + 60.0, y + 20.0)));
        strassenstueck.setattrvalue("Ordnung", "1");
        strassenstueck.setattrvalue("IstAchse", "ja");
        return strassenstueck;
    }

    private Iom_jObject benanntesGebiet(String oid, String lokalisationOid) {
        Iom_jObject benanntesGebiet = new Iom_jObject("DM01AVCH24LV95D.Gebaeudeadressen.BenanntesGebiet", oid);
        benanntesGebiet.setattrvalue("BenanntesGebiet_von", lokalisationOid);
        benanntesGebiet.addattrobj(
                "Flaeche",
                TestGeometries.surface(TestGeometries.boundary(
                        TestGeometries.coord(2601000.0, 1201000.0),
                        TestGeometries.coord(2601100.0, 1201000.0),
                        TestGeometries.coord(2601100.0, 1201100.0),
                        TestGeometries.coord(2601000.0, 1201100.0),
                        TestGeometries.coord(2601000.0, 1201000.0))));
        return benanntesGebiet;
    }

    private Set<String> errorCodes(DiagnosticCollector diagnostics) {
        return diagnostics.all().stream()
                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                .map(Diagnostic::code)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private List<Element> topLevelObjects(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(GA_NS, localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            Node parent = element.getParentNode();
            if (parent instanceof Element parentElement
                    && "Gebaeudeadressen".equals(parentElement.getLocalName())
                    && element.hasAttributeNS(ILI_NS, "tid")) {
                result.add(element);
            }
        }
        return result;
    }

    private Set<String> uniqueTids(List<Element> elements) {
        Set<String> tids = new LinkedHashSet<>();
        for (Element element : elements) {
            tids.add(element.getAttributeNS(ILI_NS, "tid"));
        }
        return tids;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element childElement
                    && GA_NS.equals(childElement.getNamespaceURI())
                    && localName.equals(childElement.getLocalName())) {
                children.add(childElement);
            }
            child = child.getNextSibling();
        }
        return children;
    }

    private String directChildText(Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        assertThat(children)
                .as("Expected child '%s' on %s", localName, parent.getLocalName())
                .hasSize(1);
        return children.get(0).getTextContent().trim();
    }
}
