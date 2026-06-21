package guru.interlis.transformer.dmav;

import static org.assertj.core.api.Assertions.assertThat;

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
import guru.interlis.transformer.mapping.ilimap.IlimapLoader;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.JobConfigNormalizer;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.support.TestGeometries;

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

class GsIlimapPilotTest {

    private static final Path PRODUCTION_YAML = Path.of("profiles/dm01-to-dmav/1.1/gs.yaml");
    private static final Path PRODUCTION_ILIMAP = Path.of("profiles/dm01-to-dmav/1.1/gs.ilimap");
    private static final String ILI_NS = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    private static final String GS_NS = "http://www.interlis.ch/xtf/2.4/DMAV_Grundstuecke_V1_1";

    @TempDir
    Path tempDir;

    @Test
    void productionProfileYamlAndIlimapProduceEquivalentJobConfig() throws Exception {
        MappingLoader yamlLoader = new MappingLoader();
        JobConfig yamlConfig = yamlLoader.load(PRODUCTION_YAML);
        JobConfigNormalizer.normalize(yamlConfig);

        IlimapLoader ilimapLoader = new IlimapLoader();
        JobConfig ilimapConfig = ilimapLoader.load(PRODUCTION_ILIMAP);
        JobConfigNormalizer.normalize(ilimapConfig);

        assertThat(ilimapConfig.version).isEqualTo(yamlConfig.version);
        assertThat(ilimapConfig.job.name).isEqualTo(yamlConfig.job.name);
        assertThat(ilimapConfig.job.description).isEqualTo(yamlConfig.job.description);
        assertThat(ilimapConfig.job.direction).isEqualTo(yamlConfig.job.direction);
        assertThat(ilimapConfig.job.failPolicy).isEqualTo(yamlConfig.job.failPolicy);
        assertThat(ilimapConfig.job.modeldir).isEqualTo(yamlConfig.job.modeldir);

        assertThat(ilimapConfig.job.inputs).hasSameSizeAs(yamlConfig.job.inputs);
        assertThat(ilimapConfig.job.outputs).hasSameSizeAs(yamlConfig.job.outputs);
        assertThat(ilimapConfig.mapping.oidStrategy.defaultStrategy)
                .isEqualTo(yamlConfig.mapping.oidStrategy.defaultStrategy);
        assertThat(ilimapConfig.mapping.oidStrategy.namespace).isEqualTo(yamlConfig.mapping.oidStrategy.namespace);
        assertThat(ilimapConfig.mapping.basketStrategy.defaultStrategy)
                .isEqualTo(yamlConfig.mapping.basketStrategy.defaultStrategy);
        assertThat(ilimapConfig.mapping.compileMode).isEqualTo(yamlConfig.mapping.compileMode);
        assertThat(ilimapConfig.mapping.enums).isEqualTo(yamlConfig.mapping.enums);

        assertThat(ilimapConfig.mapping.rules).hasSameSizeAs(yamlConfig.mapping.rules);
        for (int i = 0; i < yamlConfig.mapping.rules.size(); i++) {
            JobConfig.RuleSpec yamlRule = yamlConfig.mapping.rules.get(i);
            JobConfig.RuleSpec ilimapRule = ilimapConfig.mapping.rules.get(i);

            assertThat(ilimapRule.id).isEqualTo(yamlRule.id);
            assertThat(ilimapRule.target.output).isEqualTo(yamlRule.target.output);
            assertThat(ilimapRule.target.clazz).isEqualTo(yamlRule.target.clazz);
            assertThat(ilimapRule.sources).hasSameSizeAs(yamlRule.sources);
            assertThat(listOrEmpty(ilimapRule.joins)).hasSameSizeAs(listOrEmpty(yamlRule.joins));
            assertThat(identitySourceKeysOf(ilimapRule)).isEqualTo(identitySourceKeysOf(yamlRule));
            assertThat(ilimapRule.assign.keySet()).isEqualTo(yamlRule.assign.keySet());
            assertThat(JobConfigNormalizer.getEffectiveRefs(ilimapRule))
                    .hasSameSizeAs(JobConfigNormalizer.getEffectiveRefs(yamlRule));

            for (int sourceIndex = 0; sourceIndex < yamlRule.sources.size(); sourceIndex++) {
                JobConfig.SourceSpec yamlSource = yamlRule.sources.get(sourceIndex);
                JobConfig.SourceSpec ilimapSource = ilimapRule.sources.get(sourceIndex);
                assertThat(ilimapSource.alias).isEqualTo(yamlSource.alias);
                assertThat(JobConfigNormalizer.getInputIds(ilimapSource))
                        .isEqualTo(JobConfigNormalizer.getInputIds(yamlSource));
                assertThat(ilimapSource.clazz).isEqualTo(yamlSource.clazz);
                assertThat(normalizeQuotes(ilimapSource.where)).isEqualTo(normalizeQuotes(yamlSource.where));
            }

            List<JobConfig.JoinSpec> yamlJoins = listOrEmpty(yamlRule.joins);
            List<JobConfig.JoinSpec> ilimapJoins = listOrEmpty(ilimapRule.joins);
            for (int joinIndex = 0; joinIndex < yamlJoins.size(); joinIndex++) {
                JobConfig.JoinSpec yamlJoin = yamlJoins.get(joinIndex);
                JobConfig.JoinSpec ilimapJoin = ilimapJoins.get(joinIndex);
                assertThat(ilimapJoin.left).isEqualTo(yamlJoin.left);
                assertThat(ilimapJoin.right).isEqualTo(yamlJoin.right);
                assertThat(normalizeQuotes(ilimapJoin.on)).isEqualTo(normalizeQuotes(yamlJoin.on));
                assertThat(ilimapJoin.type).isEqualTo(yamlJoin.type);
            }

            for (String key : yamlRule.assign.keySet()) {
                assertThat(normalizeQuotes(ilimapRule.assign.get(key)))
                        .as("assign[%s]", key)
                        .isEqualTo(normalizeQuotes(yamlRule.assign.get(key)));
            }

            List<JobConfig.RefMapping> yamlRefs = JobConfigNormalizer.getEffectiveRefs(yamlRule);
            List<JobConfig.RefMapping> ilimapRefs = JobConfigNormalizer.getEffectiveRefs(ilimapRule);
            for (int refIndex = 0; refIndex < yamlRefs.size(); refIndex++) {
                JobConfig.RefMapping yamlRef = yamlRefs.get(refIndex);
                JobConfig.RefMapping ilimapRef = ilimapRefs.get(refIndex);
                assertThat(ilimapRef.association).isEqualTo(yamlRef.association);
                assertThat(ilimapRef.role).isEqualTo(yamlRef.role);
                assertThat(ilimapRef.required).isEqualTo(yamlRef.required);
                assertThat(ilimapRef.targetObject.rule).isEqualTo(yamlRef.targetObject.rule);
                assertThat(ilimapRef.targetObject.sourceRef).isEqualTo(yamlRef.targetObject.sourceRef);
            }
        }
    }

    @Test
    void productionProfileYamlAndIlimapCompileToEquivalentPlan() throws Exception {
        PreparedJob yamlPrepared = new JobRunner().prepare(materializeYaml(), new RunOptions(List.of()));
        PreparedJob ilimapPrepared = new JobRunner().prepare(materializeIlimap(), new RunOptions(List.of()));

        TransformPlan yamlPlan = yamlPrepared.plan();
        TransformPlan ilimapPlan = ilimapPrepared.plan();

        assertThat(yamlPlan.diagnostics().hasErrors())
                .as("YAML plan diagnostics: %s", yamlPlan.diagnostics().all())
                .isFalse();
        assertThat(ilimapPlan.diagnostics().hasErrors())
                .as("ILIMAP plan diagnostics: %s", ilimapPlan.diagnostics().all())
                .isFalse();

        assertThat(ilimapPlan.rules()).hasSameSizeAs(yamlPlan.rules());
        assertThat(ilimapPlan.enumMaps()).isEqualTo(yamlPlan.enumMaps());

        for (int i = 0; i < yamlPlan.rules().size(); i++) {
            RulePlan yamlRule = yamlPlan.rules().get(i);
            RulePlan ilimapRule = ilimapPlan.rules().get(i);
            assertThat(ilimapRule.ruleId()).isEqualTo(yamlRule.ruleId());
            assertThat(ilimapRule.outputId()).isEqualTo(yamlRule.outputId());
            assertThat(ilimapRule.sources()).hasSameSizeAs(yamlRule.sources());
            assertThat(ilimapRule.assignments()).hasSameSizeAs(yamlRule.assignments());
            assertThat(ilimapRule.refs()).hasSameSizeAs(yamlRule.refs());
            assertThat(ilimapRule.bags()).hasSameSizeAs(yamlRule.bags());
            assertThat(ilimapRule.joins()).hasSameSizeAs(yamlRule.joins());
            assertThat(ilimapRule.identitySourceKeys()).isEqualTo(yamlRule.identitySourceKeys());
        }
    }

    @Test
    void gsIlimapRunsSmallFixture() throws Exception {
        PreparedJob prepared = new JobRunner().prepare(materializeIlimap(), new RunOptions(List.of()));
        TransformPlan plan = prepared.plan();
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = tempDir.resolve("gs-ilimap-pilot.xtf");
        DiagnosticCollector runtimeDiagnostics = new DiagnosticCollector();

        TransformResult result;
        IoxWriter writer = new InterlisIoFactory()
                .createWriter(outputPath, plan.outputsById().get("dmav").transferDescription(), runtimeDiagnostics);
        try {
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), runtimeDiagnostics);
            result = engine.runTyped(plan, onceReaderFactory(sourceObjects()), Map.of("dmav", writer));
        } finally {
            try {
                writer.close();
            } catch (Exception ignored) {
                // TransformationEngine closes writers on the normal path.
            }
        }

        assertThat(result.sourceRecordsRead()).isEqualTo(4);
        assertThat(result.targetsCreated()).isEqualTo(4);
        assertThat(result.targetsWritten()).isEqualTo(4);
        assertThat(result.errors()).isEqualTo(0);
        assertThat(errorCodes(runtimeDiagnostics))
                .doesNotContain(
                        DiagnosticCode.RUN_DUPLICATE_TARGET_OID,
                        DiagnosticCode.RUN_REF_CARDINALITY,
                        DiagnosticCode.RUN_REF_MISSING_MANDATORY);

        Document document = parse(outputPath);
        List<Element> nachfuehrungen = topLevelObjects(document, "GSNachfuehrung");
        List<Element> grenzpunkte = topLevelObjects(document, "Grenzpunkt");

        assertThat(nachfuehrungen).hasSize(2);
        assertThat(grenzpunkte).hasSize(2);

        Map<String, Element> nachfuehrungenByIdentifikator = indexByChildText(nachfuehrungen, "Identifikator");
        Map<String, Element> grenzpunkteByNummer = indexByChildText(grenzpunkte, "Nummer");

        assertThat(directChildren(grenzpunkteByNummer.get("GP-G"), "Entstehung"))
                .hasSize(1);
        assertThat(directChildren(grenzpunkteByNummer.get("GP-P"), "Entstehung"))
                .hasSize(1);
        assertThat(directChildren(grenzpunkteByNummer.get("GP-G"), "Entstehung")
                        .get(0)
                        .getAttributeNS(ILI_NS, "ref"))
                .isEqualTo(nachfuehrungenByIdentifikator.get("GS-G").getAttributeNS(ILI_NS, "tid"));
        assertThat(directChildren(grenzpunkteByNummer.get("GP-P"), "Entstehung")
                        .get(0)
                        .getAttributeNS(ILI_NS, "ref"))
                .isEqualTo(nachfuehrungenByIdentifikator.get("GS-P").getAttributeNS(ILI_NS, "tid"));
    }

    @Test
    void gsIlimapRunsHoheitsgrenzpunktFixture() throws Exception {
        PreparedJob prepared = new JobRunner().prepare(materializeIlimap(), new RunOptions(List.of()));
        TransformPlan plan = prepared.plan();
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = tempDir.resolve("gs-ilimap-hoheitsgrenzpunkt.xtf");
        DiagnosticCollector runtimeDiagnostics = new DiagnosticCollector();

        TransformResult result;
        IoxWriter writer = new InterlisIoFactory()
                .createWriter(outputPath, plan.outputsById().get("dmav").transferDescription(), runtimeDiagnostics);
        try {
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), runtimeDiagnostics);
            result = engine.runTyped(
                    plan, onceReaderFactory(sourceObjectsWithHoheitsgrenzpunkte()), Map.of("dmav", writer));
        } finally {
            try {
                writer.close();
            } catch (Exception ignored) {
                // TransformationEngine closes writers on the normal path.
            }
        }

        assertThat(result.sourceRecordsRead()).isEqualTo(10);
        assertThat(result.targetsCreated()).isEqualTo(8);
        assertThat(result.targetsWritten()).isEqualTo(8);
        assertThat(result.errors()).isEqualTo(0);
        assertThat(errorCodes(runtimeDiagnostics))
                .doesNotContain(
                        DiagnosticCode.RUN_DUPLICATE_TARGET_OID,
                        DiagnosticCode.RUN_REF_CARDINALITY,
                        DiagnosticCode.RUN_REF_MISSING_MANDATORY);

        Document document = parse(outputPath);
        List<Element> nachfuehrungen = topLevelObjects(document, "GSNachfuehrung");
        List<Element> grenzpunkte = topLevelObjects(document, "Grenzpunkt");

        assertThat(nachfuehrungen).hasSize(4);
        assertThat(grenzpunkte).hasSize(4);

        Map<String, Element> nachfuehrungenByIdentifikator = indexByChildText(nachfuehrungen, "Identifikator");
        Map<String, Element> grenzpunkteByNummer = indexByChildText(grenzpunkte, "Nummer");

        assertThat(nachfuehrungenByIdentifikator).containsKeys("LS-G", "LS-P", "GEM-G", "GEM-P");
        assertThat(directChildText(nachfuehrungenByIdentifikator.get("LS-G"), "Mutationsart"))
                .isEqualTo("Normal");
        assertThat(directChildText(nachfuehrungenByIdentifikator.get("LS-P"), "Mutationsart"))
                .isEqualTo("Projektmutation");
        assertThat(directChildText(nachfuehrungenByIdentifikator.get("GEM-G"), "Mutationsart"))
                .isEqualTo("Normal");
        assertThat(directChildText(nachfuehrungenByIdentifikator.get("GEM-P"), "Mutationsart"))
                .isEqualTo("Projektmutation");

        assertEntstehungRef(
                grenzpunkteByNummer.get("HGP-LS-G"),
                nachfuehrungenByIdentifikator.get("LS-G").getAttributeNS(ILI_NS, "tid"));
        assertEntstehungRef(
                grenzpunkteByNummer.get("HGP-LS-P"),
                nachfuehrungenByIdentifikator.get("LS-P").getAttributeNS(ILI_NS, "tid"));
        assertEntstehungRef(
                grenzpunkteByNummer.get("HGP-GEM-G"),
                nachfuehrungenByIdentifikator.get("GEM-G").getAttributeNS(ILI_NS, "tid"));
        assertEntstehungRef(
                grenzpunkteByNummer.get("HGP-GEM-P"),
                nachfuehrungenByIdentifikator.get("GEM-P").getAttributeNS(ILI_NS, "tid"));
    }

    private Path materializeYaml() throws Exception {
        Path materialized = tempDir.resolve("gs-pilot.yaml");
        String yaml = Files.readString(PRODUCTION_YAML, StandardCharsets.UTF_8).replace("""
                  modeldir:
                    - "https://models.geo.admin.ch/"
                    - "models/"
                """, """
                  modeldir:
                    - "src/test/data/av/models"
                    - "https://models.interlis.ch"
                    - "https://models.geo.admin.ch"
                """);
        Files.writeString(materialized, yaml, StandardCharsets.UTF_8);
        return materialized;
    }

    private Path materializeIlimap() throws Exception {
        Path materialized = tempDir.resolve("gs-pilot.ilimap");
        String ilimap =
                Files.readString(PRODUCTION_ILIMAP, StandardCharsets.UTF_8).replace("""
                    modeldir "https://models.geo.admin.ch/";
                    modeldir "models/";
                """, """
                    modeldir "src/test/data/av/models";
                    modeldir "https://models.interlis.ch";
                    modeldir "https://models.geo.admin.ch";
                """);
        Files.writeString(materialized, ilimap, StandardCharsets.UTF_8);
        return materialized;
    }

    private Iom_jObject[] sourceObjects() {
        Iom_jObject nfGueltig = nachfuehrung("nf-g", "GSNB", "GS-G", "Gueltige Mutation", "gueltig", "2025-03-15");
        Iom_jObject nfProjektiert =
                nachfuehrung("nf-p", "GSNB", "GS-P", "Projektierte Mutation", "projektiert", "2025-03-16");

        Iom_jObject gpGueltig = grenzpunkt("gp-g", "nf-g", "GP-G", 2600000.0, 1200000.0, "ja", "Stein", "Ja", "nein");
        Iom_jObject gpProjektiert =
                grenzpunkt("gp-p", "nf-p", "GP-P", 2600100.0, 1200100.0, "nein", "Bolzen", "Nein", "ja");

        return new Iom_jObject[] {nfGueltig, nfProjektiert, gpGueltig, gpProjektiert};
    }

    private Iom_jObject[] sourceObjectsWithHoheitsgrenzpunkte() {
        Iom_jObject lsGueltig = nachfuehrung("ls-g", "NB-LS-G", "LS-G", "LS gueltig", "gueltig", "2025-03-15");
        Iom_jObject lsProjektiert =
                nachfuehrung("ls-p", "NB-LS-P", "LS-P", "LS projektiert", "projektiert", "2025-03-16");

        Iom_jObject gemMatchingGueltig =
                gemNachfuehrung("gem-ls-g", "NB-LS-G", "LS-G", "GEM passend gueltig", "gueltig", "2025-03-15");
        Iom_jObject gemMatchingProjektiert =
                gemNachfuehrung("gem-ls-p", "NB-LS-P", "LS-P", "GEM passend projektiert", "projektiert", "2025-03-16");
        Iom_jObject gemFallbackGueltig =
                gemNachfuehrung("gem-g", "NB-GEM-G", "GEM-G", "GEM fallback gueltig", "gueltig", "2025-03-17");
        Iom_jObject gemFallbackProjektiert =
                gemNachfuehrung("gem-p", "NB-GEM-P", "GEM-P", "GEM fallback projektiert", "projektiert", "2025-03-18");

        Iom_jObject hgpLsGueltig =
                hoheitsgrenzpunkt("hgp-ls-g", "gem-ls-g", "HGP-LS-G", 2600000.0, 1200000.0, "ja", "Bolzen", "Ja", "ja");
        Iom_jObject hgpLsProjektiert = hoheitsgrenzpunkt(
                "hgp-ls-p", "gem-ls-p", "HGP-LS-P", 2600100.0, 1200100.0, "nein", "Stein", "Nein", "nein");
        Iom_jObject hgpGemGueltig =
                hoheitsgrenzpunkt("hgp-gem-g", "gem-g", "HGP-GEM-G", 2600200.0, 1200200.0, "ja", "Kreuz", "Ja", "ja");
        Iom_jObject hgpGemProjektiert = hoheitsgrenzpunkt(
                "hgp-gem-p", "gem-p", "HGP-GEM-P", 2600300.0, 1200300.0, "nein", "Pfahl", "Nein", "nein");

        return new Iom_jObject[] {
            lsGueltig,
            lsProjektiert,
            gemMatchingGueltig,
            gemMatchingProjektiert,
            gemFallbackGueltig,
            gemFallbackProjektiert,
            hgpLsGueltig,
            hgpLsProjektiert,
            hgpGemGueltig,
            hgpGemProjektiert
        };
    }

    private Iom_jObject nachfuehrung(
            String oid, String nbIdent, String identifikator, String beschreibung, String gueltigkeit, String datum) {
        Iom_jObject nf = new Iom_jObject("DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung", oid);
        nf.setattrvalue("NBIdent", nbIdent);
        nf.setattrvalue("Identifikator", identifikator);
        nf.setattrvalue("Beschreibung", beschreibung);
        nf.setattrvalue("Gueltigkeit", gueltigkeit);
        nf.setattrvalue("GueltigerEintrag", datum);
        return nf;
    }

    private Iom_jObject gemNachfuehrung(
            String oid, String nbIdent, String identifikator, String beschreibung, String gueltigkeit, String datum) {
        Iom_jObject nf = new Iom_jObject("DM01AVCH24LV95D.Gemeindegrenzen.GEMNachfuehrung", oid);
        nf.setattrvalue("NBIdent", nbIdent);
        nf.setattrvalue("Identifikator", identifikator);
        nf.setattrvalue("Beschreibung", beschreibung);
        nf.setattrvalue("Gueltigkeit", gueltigkeit);
        nf.setattrvalue("GueltigerEintrag", datum);
        return nf;
    }

    private Iom_jObject grenzpunkt(
            String oid,
            String entstehungOid,
            String identifikator,
            double x,
            double y,
            String lageZuv,
            String punktzeichen,
            String exaktDefiniert,
            String hoheitsgrenzsteinAlt) {
        Iom_jObject gp = new Iom_jObject("DM01AVCH24LV95D.Liegenschaften.Grenzpunkt", oid);
        gp.setattrvalue("Entstehung", entstehungOid);
        gp.setattrvalue("Identifikator", identifikator);
        gp.addattrobj("Geometrie", TestGeometries.coord(x, y));
        gp.setattrvalue("LageGen", "5.0");
        gp.setattrvalue("LageZuv", lageZuv);
        gp.setattrvalue("Punktzeichen", punktzeichen);
        gp.setattrvalue("ExaktDefiniert", exaktDefiniert);
        gp.setattrvalue("HoheitsgrenzsteinAlt", hoheitsgrenzsteinAlt);
        return gp;
    }

    private Iom_jObject hoheitsgrenzpunkt(
            String oid,
            String entstehungOid,
            String identifikator,
            double x,
            double y,
            String lageZuv,
            String punktzeichen,
            String exaktDefiniert,
            String hoheitsgrenzstein) {
        Iom_jObject hgp = new Iom_jObject("DM01AVCH24LV95D.Gemeindegrenzen.Hoheitsgrenzpunkt", oid);
        hgp.setattrvalue("Entstehung", entstehungOid);
        hgp.setattrvalue("Identifikator", identifikator);
        hgp.addattrobj("Geometrie", TestGeometries.coord(x, y));
        hgp.setattrvalue("LageGen", "5.0");
        hgp.setattrvalue("LageZuv", lageZuv);
        hgp.setattrvalue("Punktzeichen", punktzeichen);
        hgp.setattrvalue("ExaktDefiniert", exaktDefiniert);
        hgp.setattrvalue("Hoheitsgrenzstein", hoheitsgrenzstein);
        return hgp;
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

    private void assertEntstehungRef(Element grenzpunkt, String expectedRef) {
        assertThat(grenzpunkt).isNotNull();
        assertThat(directChildren(grenzpunkt, "Entstehung")).hasSize(1);
        assertThat(directChildren(grenzpunkt, "Entstehung").get(0).getAttributeNS(ILI_NS, "ref"))
                .isEqualTo(expectedRef);
    }

    private static String normalizeQuotes(String expression) {
        if (expression == null) {
            return null;
        }
        return expression.replace('\'', '"');
    }

    private static <T> List<T> listOrEmpty(List<T> values) {
        return values != null ? values : List.of();
    }

    private static List<String> identitySourceKeysOf(JobConfig.RuleSpec rule) {
        return rule.identity != null && rule.identity.sourceKey != null ? rule.identity.sourceKey : List.of();
    }

    private static IoxReader createMockReader(Iom_jObject... objects) {
        return new IoxReader() {
            private int index = 0;
            private final IoxEvent[] events;

            {
                var list = new ArrayList<IoxEvent>();
                list.add(new StartTransferEvent("test", null, null));
                list.add(new StartBasketEvent("DM01AVCH24LV95D.Liegenschaften", "b1"));
                for (Iom_jObject obj : objects) {
                    list.add(new ObjectEvent(obj));
                }
                list.add(new EndBasketEvent());
                list.add(new EndTransferEvent());
                events = list.toArray(new IoxEvent[0]);
            }

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
                        public void setFactory(ch.interlis.iox.IoxFactoryCollection factory) {}
                    };
                }
                used = true;
                return createMockReader(objects);
            }
        };
    }
}
