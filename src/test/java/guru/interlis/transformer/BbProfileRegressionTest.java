package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.PreparedJob;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
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
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class BbProfileRegressionTest {

    private static final Path PROFILE = Path.of("profiles/dm01-to-dmav/1.1/bb.ilimap");
    private static final String BB_NS = "http://www.interlis.ch/xtf/2.4/DMAV_Bodenbedeckung_V1_1";

    @TempDir
    Path tempDir;

    @Test
    void specialBoFlaecheWithoutSymbolGetsSyntheticSymbolAndRealSymbolIsNotDuplicated() throws Exception {
        PreparedJob prepared = prepareProfile();
        TransformPlan plan = prepared.plan();
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = tempDir.resolve("bb-regression.xtf");
        DiagnosticCollector runtimeDiagnostics = new DiagnosticCollector();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), runtimeDiagnostics);

        TransformResult result;
        IoxWriter writer = new InterlisIoFactory()
                .createWriter(outputPath, plan.outputsById().get("dmav").transferDescription(), runtimeDiagnostics);
        try {
            result = engine.runTyped(
                    plan, inputId -> TestMockReaders.mockReader(sourceObjects()), Map.of("dmav", writer));
        } finally {
            try {
                writer.close();
            } catch (Exception ignored) {
                // TransformationEngine closes writers on the normal path.
            }
        }

        assertThat(result.errors()).isEqualTo(0);
        assertThat(runtimeDiagnostics.all()).noneMatch(d -> d.code().equals(DiagnosticCode.RUN_BAG_MANDATORY_MISSING));
        assertThat(engine.getLossinessCollector().events())
                .extracting(event -> event.reasonCode())
                .containsExactly("SYNTHETIC_BB_SYMBOLPOSITION");

        Document document = parse(outputPath);
        List<Element> bodenbedeckungen = topLevelObjects(document, "Bodenbedeckung.Bodenbedeckung");
        assertThat(bodenbedeckungen).hasSize(2);

        Element wasserbecken = byChildText(bodenbedeckungen, "Bodenbedeckungsart", "befestigt.Wasserbecken");
        Element stehendesGewaesser =
                byChildText(bodenbedeckungen, "Bodenbedeckungsart", "Gewaesser.stehendes_Gewaesser");

        List<Element> syntheticSymbols = directChildren(wasserbecken, "Symbolposition");
        assertThat(syntheticSymbols).hasSize(1);
        assertThat(descendantText(syntheticSymbols.get(0), "Orientierung")).startsWith("100");

        List<Element> realSymbols = directChildren(stehendesGewaesser, "Symbolposition");
        assertThat(realSymbols).hasSize(1);
        assertThat(descendantText(realSymbols.get(0), "Orientierung")).startsWith("180");
    }

    private PreparedJob prepareProfile() throws Exception {
        Path materializedProfile = tempDir.resolve("bb-regression.ilimap");
        String ilimap = Files.readString(PROFILE, StandardCharsets.UTF_8).replace("""
              modeldir "https://models.geo.admin.ch/";
              modeldir "models/";
        """, """
              modeldir "src/test/data/av/models";
              modeldir "https://models.interlis.ch";
              modeldir "https://models.geo.admin.ch";
        """);
        Files.writeString(materializedProfile, ilimap, StandardCharsets.UTF_8);
        return new JobRunner().prepare(materializedProfile, new RunOptions(List.of()));
    }

    private Iom_jObject[] sourceObjects() {
        Iom_jObject nf = new Iom_jObject("DM01AVCH24LV95D.Bodenbedeckung.BBNachfuehrung", "nf-1");
        nf.setattrvalue("NBIdent", "BB_NB");
        nf.setattrvalue("Identifikator", "BB_ID001");
        nf.setattrvalue("Beschreibung", "BbProfileRegression");
        nf.setattrvalue("Gueltigkeit", "gueltig");
        nf.setattrvalue("GueltigerEintrag", "2025-03-15");

        Iom_jObject withoutSymbol = boFlaeche("bb-no-symbol", "nf-1", "befestigt.Wasserbecken", 2600000.0, 1200000.0);
        Iom_jObject withSymbol = boFlaeche("bb-with-symbol", "nf-1", "Gewaesser.stehendes", 2600200.0, 1200200.0);
        Iom_jObject symbol = new Iom_jObject("DM01AVCH24LV95D.Bodenbedeckung.BoFlaecheSymbol", "sym-1");
        symbol.setattrvalue("BoFlaecheSymbol_von", "bb-with-symbol");
        symbol.addattrobj("Pos", TestGeometries.coord(2600250.0, 1200250.0));
        symbol.setattrvalue("Ori", "180.0");

        return new Iom_jObject[] {nf, withoutSymbol, withSymbol, symbol};
    }

    private Iom_jObject boFlaeche(String oid, String entstehungOid, String art, double x, double y) {
        Iom_jObject boFlaeche = new Iom_jObject("DM01AVCH24LV95D.Bodenbedeckung.BoFlaeche", oid);
        boFlaeche.setattrvalue("Entstehung", entstehungOid);
        boFlaeche.addattrobj(
                "Geometrie",
                TestGeometries.surface(TestGeometries.boundary(
                        TestGeometries.coord(x, y),
                        TestGeometries.coord(x + 80.0, y),
                        TestGeometries.coord(x + 80.0, y + 60.0),
                        TestGeometries.coord(x, y + 60.0),
                        TestGeometries.coord(x, y))));
        boFlaeche.setattrvalue("Qualitaet", "AV93");
        boFlaeche.setattrvalue("Art", art);
        return boFlaeche;
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private List<Element> topLevelObjects(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(BB_NS, localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            Node parent = element.getParentNode();
            if (parent instanceof Element parentElement
                    && "Bodenbedeckung".equals(parentElement.getLocalName())
                    && element.hasAttributeNS("http://www.interlis.ch/xtf/2.4/INTERLIS", "tid")) {
                result.add(element);
            }
        }
        return result;
    }

    private Element byChildText(List<Element> elements, String childName, String expectedText) {
        return elements.stream()
                .filter(element -> expectedText.equals(directChildText(element, childName)))
                .findFirst()
                .orElseThrow();
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element childElement
                    && BB_NS.equals(childElement.getNamespaceURI())
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
        return children.get(0).getTextContent().trim();
    }

    private String descendantText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        assertThat(nodes.getLength()).isGreaterThan(0);
        return nodes.item(0).getTextContent().trim();
    }
}
