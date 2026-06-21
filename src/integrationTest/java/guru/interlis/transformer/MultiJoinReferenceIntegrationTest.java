package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.PreparedJob;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.support.TestGeometries;

import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
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

class MultiJoinReferenceIntegrationTest {

    private static final String ILI_NS = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    private static final String GS_NS = "http://www.interlis.ch/xtf/2.4/DMAV_Grundstuecke_V1_1";

    @TempDir
    Path tempDir;

    @Test
    void resolvesRequiredRefAgainstNonDriverAliasAcrossMultipleJoins() throws Exception {
        Path mappingPath = tempDir.resolve("multi-join-reference.yaml");
        Files.writeString(mappingPath, mappingYaml(), StandardCharsets.UTF_8);

        PreparedJob prepared = new JobRunner().prepare(mappingPath, new RunOptions(List.of()));
        TransformPlan plan = prepared.plan();
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = tempDir.resolve("multi-join-reference.xtf");
        DiagnosticCollector runtimeDiagnostics = new DiagnosticCollector();

        TransformResult result;
        IoxWriter writer = new InterlisIoFactory()
                .createWriter(outputPath, plan.outputsById().get("dmav").transferDescription(), runtimeDiagnostics);
        try {
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), runtimeDiagnostics);
            result = engine.runTyped(plan, inputId -> sourceReader(), Map.of("dmav", writer));
        } finally {
            try {
                writer.close();
            } catch (Exception ignored) {
                // TransformationEngine closes writers on the normal path.
            }
        }

        assertThat(result.sourceRecordsRead()).isEqualTo(3);
        assertThat(result.targetsCreated()).isEqualTo(2);
        assertThat(result.targetsWritten()).isEqualTo(2);
        assertThat(result.errors()).isEqualTo(0);
        assertThat(errorCodes(runtimeDiagnostics))
                .doesNotContain(
                        DiagnosticCode.RUN_DUPLICATE_TARGET_OID,
                        DiagnosticCode.RUN_JOIN_MISSING,
                        DiagnosticCode.RUN_REF_CARDINALITY,
                        DiagnosticCode.RUN_REF_MISSING_MANDATORY);

        Document document = parse(outputPath);
        List<Element> nachfuehrungen = topLevelObjects(document, "GSNachfuehrung");
        List<Element> grenzpunkte = topLevelObjects(document, "Grenzpunkt");

        assertThat(nachfuehrungen).hasSize(1);
        assertThat(grenzpunkte).hasSize(1);

        Element nachfuehrung = nachfuehrungen.get(0);
        Element grenzpunkt = grenzpunkte.get(0);
        assertThat(directChildText(nachfuehrung, "Identifikator")).isEqualTo("JOIN-ID");
        assertThat(directChildText(grenzpunkt, "Nummer")).isEqualTo("HGP-JOIN");
        assertThat(directChildren(grenzpunkt, "Entstehung")).hasSize(1);
        assertThat(directChildren(grenzpunkt, "Entstehung").get(0).getAttributeNS(ILI_NS, "ref"))
                .isEqualTo(nachfuehrung.getAttributeNS(ILI_NS, "tid"));
    }

    private String mappingYaml() {
        return """
                version: 1

                job:
                  name: multi-join-reference
                  description: "Regression for refs against non-driver aliases"
                  direction: dm01-to-dmav
                  failPolicy: strict
                  modeldir:
                    - "src/test/data/av/models"
                    - "https://models.interlis.ch"
                    - "https://models.geo.admin.ch"
                  inputs:
                    - id: dm01
                      path: "input/dm01.itf"
                      model: "DM01AVCH24LV95D"
                      format: "itf"
                  outputs:
                    - id: dmav
                      path: "build/out/multi-join-reference.xtf"
                      model: "DMAV_Grundstuecke_V1_1"
                      format: "xtf"

                mapping:
                  compileMode: compatible
                  oidStrategy:
                    default: deterministicUuid
                    namespace: "multi-join-reference"
                  basketStrategy:
                    default: byTopic

                  enums:
                    Zuverlaessigkeit_DM01_DMAV:
                      ja: "true"
                      nein: "false"
                    JaNein_DM01_DMAV:
                      Ja: "true"
                      Nein: "false"
                    ja_nein_DM01_DMAV:
                      ja: "true"
                      nein: "false"
                    Versicherungsart_DM01_DMAV:
                      Stein: "Stein"
                      Kunststoffzeichen: "Kunststoffzeichen"
                      Bolzen: "Bolzen"
                      Rohr: "Rohr"
                      Pfahl: "Pfahl"
                      Kreuz: "Kreuz"
                      unversichert: "unversichert"
                      weitere: "weitere"
                    Status_Mutationsart:
                      gueltig: "Normal"
                      projektiert: "Projektmutation"

                  rules:
                    - id: gs-nachfuehrung
                      target:
                        output: dmav
                        class: "DMAV_Grundstuecke_V1_1.Grundstuecke.GSNachfuehrung"
                      sources:
                        - alias: ls
                          input: dm01
                          class: "DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung"
                      identity:
                        sourceKey: ["ls.NBIdent", "ls.Identifikator"]
                      assign:
                        NBIdent: "ls.NBIdent"
                        Identifikator: "ls.Identifikator"
                        Beschreibung: "truncate(ls.Beschreibung, 60)"
                        Mutationsart: "enumMap(coalesce(ls.Gueltigkeit, #gueltig), 'Status_Mutationsart')"
                        GueltigerEintrag: "toXmlDateTime(ls.GueltigerEintrag)"

                    - id: hoheitsgrenzpunkt
                      target:
                        output: dmav
                        class: "DMAV_Grundstuecke_V1_1.Grundstuecke.Grenzpunkt"
                      sources:
                        - alias: hgp
                          input: dm01
                          class: "DM01AVCH24LV95D.Gemeindegrenzen.Hoheitsgrenzpunkt"
                        - alias: gem
                          input: dm01
                          class: "DM01AVCH24LV95D.Gemeindegrenzen.GEMNachfuehrung"
                        - alias: ls
                          input: dm01
                          class: "DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung"
                      joins:
                        - left: hgp
                          right: gem
                          on: "eq(hgp.Entstehung, gem)"
                          type: "inner"
                        - left: gem
                          right: ls
                          on: "eq(gem.Identifikator, ls.Identifikator)"
                          type: "inner"
                      where: "ls.NBIdent == gem.NBIdent"
                      identity:
                        sourceKey: ["hgp.Identifikator"]
                      assign:
                        Nummer: "hgp.Identifikator"
                        Geometrie: "hgp.Geometrie"
                        Lagegenauigkeit: "div(hgp.LageGen, 100.0)"
                        IstLagezuverlaessig: "enumMap(hgp.LageZuv, 'Zuverlaessigkeit_DM01_DMAV')"
                        Punktzeichen: "enumMap(hgp.Punktzeichen, 'Versicherungsart_DM01_DMAV')"
                        IstExaktDefiniert: "enumMap(hgp.ExaktDefiniert, 'JaNein_DM01_DMAV')"
                        IstHoheitsgrenzpunkt: "true"
                        IstHoheitsgrenzsteinAlt: "enumMap(hgp.Hoheitsgrenzstein, 'ja_nein_DM01_DMAV')"
                      refs:
                        - association: "Entstehung_Grenzpunkt"
                          role: "Entstehung"
                          required: true
                          targetObject:
                            rule: gs-nachfuehrung
                            sourceRef: "ls"
                """;
    }

    private IoxReader sourceReader() {
        Iom_jObject ls = new Iom_jObject("DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung", "ls-1");
        ls.setattrvalue("NBIdent", "JOIN-NB");
        ls.setattrvalue("Identifikator", "JOIN-ID");
        ls.setattrvalue("Beschreibung", "LS Nachfuehrung");
        ls.setattrvalue("Gueltigkeit", "gueltig");
        ls.setattrvalue("GueltigerEintrag", "2025-03-15");

        Iom_jObject gem = new Iom_jObject("DM01AVCH24LV95D.Gemeindegrenzen.GEMNachfuehrung", "gem-1");
        gem.setattrvalue("NBIdent", "JOIN-NB");
        gem.setattrvalue("Identifikator", "JOIN-ID");
        gem.setattrvalue("Beschreibung", "GEM Nachfuehrung");
        gem.setattrvalue("Gueltigkeit", "gueltig");
        gem.setattrvalue("GueltigerEintrag", "2025-03-15");

        Iom_jObject hgp = new Iom_jObject("DM01AVCH24LV95D.Gemeindegrenzen.Hoheitsgrenzpunkt", "hgp-1");
        hgp.setattrvalue("Entstehung", "gem-1");
        hgp.setattrvalue("Identifikator", "HGP-JOIN");
        hgp.addattrobj("Geometrie", TestGeometries.coord(2600000.0, 1200000.0));
        hgp.setattrvalue("LageGen", "5.0");
        hgp.setattrvalue("LageZuv", "ja");
        hgp.setattrvalue("Punktzeichen", "Bolzen");
        hgp.setattrvalue("Hoheitsgrenzstein", "ja");
        hgp.setattrvalue("ExaktDefiniert", "Ja");

        List<IoxEvent> events = new ArrayList<>();
        events.add(new StartTransferEvent("test", null, null));
        events.add(new StartBasketEvent("DM01AVCH24LV95D.Liegenschaften", "b-ls"));
        events.add(new ObjectEvent(ls));
        events.add(new EndBasketEvent());
        events.add(new StartBasketEvent("DM01AVCH24LV95D.Gemeindegrenzen", "b-gem"));
        events.add(new ObjectEvent(gem));
        events.add(new ObjectEvent(hgp));
        events.add(new EndBasketEvent());
        events.add(new EndTransferEvent());

        Iterator<IoxEvent> iterator = events.iterator();
        IoxReader reader = mock(IoxReader.class);
        try {
            when(reader.read()).thenAnswer(invocation -> iterator.hasNext() ? iterator.next() : null);
            when(reader.createIomObject(anyString(), anyString()))
                    .thenAnswer(invocation -> new Iom_jObject(invocation.getArgument(0), invocation.getArgument(1)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return reader;
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
        NodeList nodes = document.getElementsByTagNameNS(GS_NS, localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            Node parent = element.getParentNode();
            if (parent instanceof Element parentElement
                    && "Grundstuecke".equals(parentElement.getLocalName())
                    && element.hasAttributeNS(ILI_NS, "tid")) {
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
                    && GS_NS.equals(childElement.getNamespaceURI())
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
