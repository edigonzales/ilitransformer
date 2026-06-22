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

class Fpds2ProfileRegressionTest {

    private static final Path PROFILE = Path.of("profiles/dm01-to-dmav/1.1/fpds2.ilimap");
    private static final String FPDS2_NS = "http://www.interlis.ch/xtf/2.4/KGKCGC_FPDS2_V1_1";

    @TempDir
    Path tempDir;

    @Test
    void duplicateHfp2NachfuehrungWithSameBusinessKeyIsSkipped() throws Exception {
        PreparedJob prepared = prepareProfile();
        TransformPlan plan = prepared.plan();
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = tempDir.resolve("fpds2-regression.xtf");
        DiagnosticCollector runtimeDiagnostics = new DiagnosticCollector();

        TransformResult result;
        TransformationEngine engine;
        IoxWriter writer = new InterlisIoFactory()
                .createWriter(outputPath, plan.outputsById().get("dmav").transferDescription(), runtimeDiagnostics);
        try {
            engine = new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), runtimeDiagnostics);
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
        assertThat(runtimeDiagnostics.all()).noneMatch(d -> d.code().equals(DiagnosticCode.RUN_DUPLICATE_TARGET_OID));
        assertThat(engine.getLossinessCollector().events())
                .extracting(event -> event.reasonCode())
                .containsExactly("DROPPED_DUPLICATE_FPDS2_NACHFUEHRUNG");
        assertThat(engine.getLossinessCollector().events())
                .extracting(event -> event.sourceClass())
                .containsExactly("DM01AVCH24LV95D.FixpunkteKategorie2.HFP2Nachfuehrung");

        Document document = parse(outputPath);
        List<Element> nachfuehrungen = topLevelObjects(document, "FixpunkteNachfuehrung");
        assertThat(nachfuehrungen).hasSize(1);
        assertThat(directChildText(nachfuehrungen.get(0), "NBIdent")).isEqualTo("SO0100000001");
        assertThat(directChildText(nachfuehrungen.get(0), "Identifikator")).isEqualTo("0");
    }

    private PreparedJob prepareProfile() throws Exception {
        Path materializedProfile = tempDir.resolve("fpds2-regression.ilimap");
        String ilimap = Files.readString(PROFILE, StandardCharsets.UTF_8).replace("""
              modeldir "https://models.geo.admin.ch/";
              modeldir "https://models.kgk-cgc.ch/";
              modeldir "models/";
        """, """
              modeldir "src/test/data/av/models";
              modeldir "https://models.interlis.ch";
              modeldir "https://models.geo.admin.ch";
              modeldir "https://models.kgk-cgc.ch";
        """);
        Files.writeString(materializedProfile, ilimap, StandardCharsets.UTF_8);
        return new JobRunner().prepare(materializedProfile, new RunOptions(List.of()));
    }

    private Iom_jObject[] sourceObjects() {
        Iom_jObject lfp2 = new Iom_jObject("DM01AVCH24LV95D.FixpunkteKategorie2.LFP2Nachfuehrung", "lfp2-nf");
        lfp2.setattrvalue("NBIdent", "SO0100000001");
        lfp2.setattrvalue("Identifikator", "0");
        lfp2.setattrvalue("Beschreibung", "LFP2 duplicate key");
        lfp2.setattrvalue("GueltigerEintrag", "2024-01-01");

        Iom_jObject hfp2 = new Iom_jObject("DM01AVCH24LV95D.FixpunkteKategorie2.HFP2Nachfuehrung", "hfp2-nf");
        hfp2.setattrvalue("NBIdent", "SO0100000001");
        hfp2.setattrvalue("Identifikator", "0");
        hfp2.setattrvalue("Beschreibung", "HFP2 duplicate key");
        hfp2.setattrvalue("GueltigerEintrag", "2024-01-01");

        return new Iom_jObject[] {lfp2, hfp2};
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private List<Element> topLevelObjects(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(FPDS2_NS, localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            Node parent = element.getParentNode();
            if (parent instanceof Element parentElement
                    && "FPDS2".equals(parentElement.getLocalName())
                    && element.hasAttributeNS("http://www.interlis.ch/xtf/2.4/INTERLIS", "tid")) {
                result.add(element);
            }
        }
        return result;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element childElement
                    && FPDS2_NS.equals(childElement.getNamespaceURI())
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
}
