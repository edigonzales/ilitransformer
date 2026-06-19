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
import java.util.LinkedHashMap;
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

class EoProfileRegressionTest {

    private static final Path PROFILE = Dm01DmavFixtures.EO.dm01ToDmavProfile();
    private static final String ILI_NS = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    private static final String EO_NS = "http://www.interlis.ch/xtf/2.4/DMAV_Einzelobjekte_V1_1";

    @TempDir
    Path tempDir;

    @Test
    void forwardProfileResolvesProjectedEntstehungForEinzelobjekteAndMesspunkte() throws Exception {
        PreparedJob prepared = prepareProfile();
        TransformPlan plan = prepared.plan();
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = tempDir.resolve("eo-regression.xtf");
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

        assertThat(result.targetsCreated()).isEqualTo(7);
        assertThat(result.errors()).isEqualTo(0);
        assertThat(errorCodes(runtimeDiagnostics))
                .doesNotContain(
                        DiagnosticCode.RUN_DUPLICATE_TARGET_OID,
                        DiagnosticCode.RUN_REF_CARDINALITY,
                        DiagnosticCode.RUN_REF_MISSING_MANDATORY);

        Document document = parse(outputPath);

        List<Element> nachfuehrungen = topLevelObjects(document, "EONachfuehrung");
        List<Element> einzelobjekte = topLevelObjects(document, "Einzelobjekt");
        List<Element> messpunkte = topLevelObjects(document, "Messpunkt");

        assertThat(nachfuehrungen).hasSize(2);
        assertThat(einzelobjekte).hasSize(2);
        assertThat(messpunkte).hasSize(3);
        assertThat(uniqueTids(einzelobjekte)).hasSize(2);
        assertThat(uniqueTids(messpunkte)).hasSize(3);

        Map<String, Element> einzelobjekteByArt = indexByChildText(einzelobjekte, "Einzelobjektart");
        assertThat(einzelobjekteByArt).containsKeys("Brunnen", "Quelle");
        assertThat(directChildText(einzelobjekteByArt.get("Brunnen"), "Objektstatus")).isEqualTo("real");
        assertThat(directChildText(einzelobjekteByArt.get("Quelle"), "Objektstatus")).isEqualTo("projektiert");
        assertThat(directChildren(einzelobjekteByArt.get("Brunnen"), "Entstehung")).hasSize(1);
        assertThat(directChildren(einzelobjekteByArt.get("Quelle"), "Entstehung")).hasSize(1);
        assertThat(directChildren(einzelobjekteByArt.get("Brunnen"), "Entstehung")
                        .get(0)
                        .getAttributeNS(ILI_NS, "ref"))
                .isNotBlank();
        assertThat(directChildren(einzelobjekteByArt.get("Quelle"), "Entstehung")
                        .get(0)
                        .getAttributeNS(ILI_NS, "ref"))
                .isNotBlank();

        Map<String, Element> messpunkteByNummer = indexByChildText(messpunkte, "Nummer");
        assertThat(messpunkteByNummer).containsKeys("MP-G", "MP-P", "MP-O");
        assertThat(directChildren(messpunkteByNummer.get("MP-G"), "Entstehung")).hasSize(1);
        assertThat(directChildren(messpunkteByNummer.get("MP-P"), "Entstehung")).hasSize(1);
        assertThat(directChildren(messpunkteByNummer.get("MP-O"), "Entstehung")).isEmpty();
    }

    private PreparedJob prepareProfile() throws Exception {
        Path materializedProfile = tempDir.resolve("eo-regression.yaml");
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
        Iom_jObject nfGueltig = nachfuehrung("nf-g", "EO_NB", "EO-G", "Gueltig", "gueltig", "2025-03-15");
        Iom_jObject nfProjektiert =
                nachfuehrung("nf-p", "EO_NB", "EO-P", "Projektiert", "projektiert", "2025-03-16");

        Iom_jObject eoGueltig = einzelobjekt("eo-g", "nf-g", "AV93", "Brunnen");
        Iom_jObject eoProjektiert = einzelobjekt("eo-p", "nf-p", "AV93", "Quelle");

        Iom_jObject mpGueltig = messpunkt("mp-g", "nf-g", "MP-G", 2600010.0, 1200010.0, "ja", "Ja");
        Iom_jObject mpProjektiert = messpunkt("mp-p", "nf-p", "MP-P", 2600020.0, 1200020.0, "nein", "Nein");
        Iom_jObject mpOhne = messpunkt("mp-o", null, "MP-O", 2600030.0, 1200030.0, "ja", "Ja");

        return new Iom_jObject[] {nfGueltig, nfProjektiert, eoGueltig, eoProjektiert, mpGueltig, mpProjektiert, mpOhne};
    }

    private Iom_jObject nachfuehrung(
            String oid, String nbIdent, String identifikator, String beschreibung, String gueltigkeit, String datum) {
        Iom_jObject nf = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.EONachfuehrung", oid);
        nf.setattrvalue("NBIdent", nbIdent);
        nf.setattrvalue("Identifikator", identifikator);
        nf.setattrvalue("Beschreibung", beschreibung);
        nf.setattrvalue("Gueltigkeit", gueltigkeit);
        nf.setattrvalue("GueltigerEintrag", datum);
        return nf;
    }

    private Iom_jObject einzelobjekt(String oid, String entstehungOid, String qualitaet, String art) {
        Iom_jObject eo = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Einzelobjekt", oid);
        eo.setattrvalue("Entstehung", entstehungOid);
        eo.setattrvalue("Qualitaet", qualitaet);
        eo.setattrvalue("Art", art);
        return eo;
    }

    private Iom_jObject messpunkt(
            String oid, String entstehungOid, String identifikator, double x, double y, String lageZuv, String exaktDefiniert) {
        Iom_jObject ep = new Iom_jObject("DM01AVCH24LV95D.Einzelobjekte.Einzelpunkt", oid);
        if (entstehungOid != null) {
            ep.setattrvalue("Entstehung", entstehungOid);
        }
        ep.setattrvalue("Identifikator", identifikator);
        ep.addattrobj("Geometrie", TestGeometries.coord(x, y));
        ep.setattrvalue("LageGen", "5.0");
        ep.setattrvalue("LageZuv", lageZuv);
        ep.setattrvalue("ExaktDefiniert", exaktDefiniert);
        return ep;
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
        NodeList nodes = document.getElementsByTagNameNS(EO_NS, localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            Node parent = element.getParentNode();
            if (parent instanceof Element parentElement
                    && "Einzelobjekte".equals(parentElement.getLocalName())
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

    private Map<String, Element> indexByChildText(List<Element> elements, String childName) {
        Map<String, Element> indexed = new LinkedHashMap<>();
        for (Element element : elements) {
            indexed.put(directChildText(element, childName), element);
        }
        return indexed;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element childElement
                    && EO_NS.equals(childElement.getNamespaceURI())
                    && localName.equals(childElement.getLocalName())) {
                children.add(childElement);
            }
            child = child.getNextSibling();
        }
        return children;
    }

    private String directChildText(Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        assertThat(children).hasSize(1);
        return children.get(0).getTextContent();
    }
}
