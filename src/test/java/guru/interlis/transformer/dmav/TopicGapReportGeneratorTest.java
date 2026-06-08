package guru.interlis.transformer.dmav;

import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.ModelInventory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class TopicGapReportGeneratorTest {

    @Test
    void generatesReportFromSyntheticData() throws Exception {
        List<CorrelationHint> hints = createSyntheticHints();
        List<MappingCandidate> candidates = createSyntheticCandidates();
        Map<String, ModelInventory> dmavInventories = Map.of();
        ModelInventory dm01Inventory = null;
        Map<String, IliModelCompileResult> compileResults = Map.of();

        TopicGapReportGenerator generator = new TopicGapReportGenerator(
                hints, candidates, dmavInventories, dm01Inventory, compileResults);

        TopicGapReportGenerator.GapReport report = generator.generate();

        // Verify summary
        assertThat(report.summary().totalHints()).isEqualTo(9);
        assertThat(report.summary().totalCandidates()).isGreaterThanOrEqualTo(9);

        // Verify topic analyses exist for all expected topics
        assertThat(report.topicAnalyses()).containsKeys(
                "FixpunkteKategorie3", "Liegenschaften", "Bodenbedeckung",
                "Einzelobjekte", "Nomenklatur");

        // Fixpunkte should be PILOT IMPLEMENTED
        TopicGapReportGenerator.TopicAnalysis lfp3 = report.topicAnalyses().get("FixpunkteKategorie3");
        assertThat(lfp3).isNotNull();
        assertThat(lfp3.pilotStatus()).contains("PILOT");

        // Liegenschaften should have risk flags
        TopicGapReportGenerator.TopicAnalysis lieg = report.topicAnalyses().get("Liegenschaften");
        assertThat(lieg).isNotNull();
        assertThat(lieg.riskFlags()).isNotEmpty();

        // Recommended slices should be non-empty
        assertThat(report.recommendedSlices()).isNotEmpty();
    }

    @Test
    void writeReportProducesValidMarkdown() throws Exception {
        List<CorrelationHint> hints = createSyntheticHints();
        List<MappingCandidate> candidates = createSyntheticCandidates();

        TopicGapReportGenerator generator = new TopicGapReportGenerator(
                hints, candidates, Map.of(), null, Map.of());

        TopicGapReportGenerator.GapReport report = generator.generate();

        Path reportPath = Files.createTempFile("topic-gap-report-", ".md");
        try {
            generator.writeReport(report, reportPath);
            String content = Files.readString(reportPath);

            // Verify expected sections
            assertThat(content).contains("# DM01");
            assertThat(content).contains("## 1. Summary");
            assertThat(content).contains("## 2. Topic Analysis");
            assertThat(content).contains("## 3. Cross-Cutting Concerns");
            assertThat(content).contains("## 4. Recommended Next Slices");
            assertThat(content).contains("## 5. Open Questions Summary");
            assertThat(content).contains("## 6. Source");

            // Verify topics are listed
            assertThat(content).contains("FixpunkteKategorie3");
            assertThat(content).contains("PILOT");
            assertThat(content).contains("Liegenschaften");
            assertThat(content).contains("Bodenbedeckung");

            // Verify complexity classifications exist (at least one non-manual)
            assertThat(content).contains("einfach");
            assertThat(content).contains("mittel");

        } finally {
            Files.deleteIfExists(reportPath);
        }
    }

    @Test
    void highRiskTopicsDetectedCorrectly() throws Exception {
        List<CorrelationHint> hints = createSyntheticHints();
        List<MappingCandidate> candidates = createSyntheticCandidates();

        TopicGapReportGenerator generator = new TopicGapReportGenerator(
                hints, candidates, Map.of(), null, Map.of());

        TopicGapReportGenerator.GapReport report = generator.generate();

        // At least one topic should have risk flags (AREA/LINEATTR/etc.)
        boolean hasRiskFlags = report.topicAnalyses().values().stream()
                .anyMatch(a -> !a.riskFlags().isEmpty());
        assertThat(hasRiskFlags).isTrue();

        // FixpunkteKategorie3 (simple point data) should be einfach or mittel
        String lfp3Complexity = report.topicAnalyses().get("FixpunkteKategorie3").complexity();
        assertThat(lfp3Complexity).isIn("einfach", "mittel");
    }

    // -- Synthetic test data --------------------------------------------

    private static List<CorrelationHint> createSyntheticHints() {
        List<CorrelationHint> hints = new ArrayList<>();

        hints.add(hint(1, Direction.DM01_TO_DMAV,
                "FixpunkteKategorie3", "LFP3", "NBIdent",
                "FixpunkteAVKategorie3", "LFP3", "NBIdent", "K", 0.7));
        hints.add(hint(2, Direction.DM01_TO_DMAV,
                "FixpunkteKategorie3", "LFP3", "Nummer",
                "FixpunkteAVKategorie3", "LFP3", "Nummer", "K", 0.7));
        hints.add(hint(3, Direction.DM01_TO_DMAV,
                "FixpunkteKategorie3", "LFP3", "Geometrie",
                "FixpunkteAVKategorie3", "LFP3", "Geometrie", "K", 0.7));
        hints.add(hint(4, Direction.DM01_TO_DMAV,
                "Liegenschaften", "Liegenschaft", "NBIdent",
                "Grundstuecke", "Grundstueck", "NBIdent", "K", 0.7));
        hints.add(hint(5, Direction.DM01_TO_DMAV,
                "Liegenschaften", "Liegenschaft", "Geometrie",
                "Grundstuecke", "Grundstueck", "Geometrie", "V", 0.5));
        hints.add(hint(6, Direction.DM01_TO_DMAV,
                "Bodenbedeckung", "BoFlaeche", "Geometrie",
                "Bodenbedeckung", "Bodenbedeckungsflaeche", "Geometrie", "V", 0.5));
        hints.add(hint(7, Direction.DM01_TO_DMAV,
                "Einzelobjekte", "Flaechenelement", "NBIdent",
                "Einzelobjekte", "Flaechenelement", "NBIdent", "K", 0.7));
        hints.add(hint(8, Direction.DM01_TO_DMAV,
                "Einzelobjekte", "Linienelement", "NBIdent",
                "Einzelobjekte", "Linienelement", "NBIdent", "K", 0.7));
        hints.add(hint(9, Direction.DM01_TO_DMAV,
                "Nomenklatur", "Flurname", "NBIdent",
                "Nomenklatur", "Flurname", "NBIdent", "K", 0.7));

        return hints;
    }

    private static List<MappingCandidate> createSyntheticCandidates() {
        List<MappingCandidate> candidates = new ArrayList<>();

        // FixpunkteKategorie3 (LFP3) - simple, already piloted
        candidates.add(candidate("FIX_1", Direction.DM01_TO_DMAV,
                "DM01.Fixpunkte.FixpunkteKategorie3.LFP3", "NBIdent",
                "DMAV.FixpunkteAVKategorie3.LFP3", "NBIdent",
                "high", 0.9, "XLSX"));
        candidates.add(candidate("FIX_2", Direction.DM01_TO_DMAV,
                "DM01.Fixpunkte.FixpunkteKategorie3.LFP3", "Geometrie",
                "DMAV.FixpunkteAVKategorie3.LFP3", "Geometrie",
                "high", 0.85, "XLSX"));

        // Liegenschaften - complex with AREA/LINEATTR
        candidates.add(candidate("LIEG_1", Direction.DM01_TO_DMAV,
                "DM01.Liegenschaften.Liegenschaft", "NBIdent",
                "DMAV.Grundstuecke.Grundstueck", "NBIdent",
                "medium", 0.6, "XLSX"));
        candidates.add(candidate("LIEG_2", Direction.DM01_TO_DMAV,
                "DM01.Liegenschaften.Liegenschaft", "Linienart_Geometrie",
                "DMAV.Grundstuecke.Grundstueck", "Linienart",
                "low", 0.35, "XLSX"));
        candidates.add(candidate("LIEG_3", Direction.DM01_TO_DMAV,
                "DM01.Liegenschaften.Grenzpunkt", "Geometrie",
                "DMAV.Grundstuecke.Grenzpunkt", "Geometrie",
                "medium", 0.65, "XLSX"));

        // Bodenbedeckung - AREA without LINEATTR
        candidates.add(candidate("BB_1", Direction.DM01_TO_DMAV,
                "DM01.Bodenbedeckung.BoFlaeche", "NBIdent",
                "DMAV.Bodenbedeckung.Bodenbedeckungsflaeche", "NBIdent",
                "medium", 0.6, "XLSX"));
        candidates.add(candidate("BB_2", Direction.DM01_TO_DMAV,
                "DM01.Bodenbedeckung.BoFlaeche", "Area_Geometrie",
                "DMAV.Bodenbedeckung.Bodenbedeckungsflaeche", "Geometrie",
                "low", 0.3, "XLSX"));

        // Einzelobjekte - mixed POLYLINE/SURFACE
        candidates.add(candidate("EO_1", Direction.DM01_TO_DMAV,
                "DM01.Einzelobjekte.Flaechenelement", "NBIdent",
                "DMAV.Einzelobjekte.Flaechenelement", "NBIdent",
                "medium", 0.7, "SYNONYM"));
        candidates.add(candidate("EO_2", Direction.DM01_TO_DMAV,
                "DM01.Einzelobjekte.Linienelement", "NBIdent",
                "DMAV.Einzelobjekte.Linienelement", "NBIdent",
                "medium", 0.7, "SYNONYM"));

        return candidates;
    }

    private static CorrelationHint hint(int row, Direction dir,
                                         String sourceTopic, String sourceClass, String sourceAttr,
                                         String targetTopic, String targetClass, String targetAttr,
                                         String code, double confidence) {
        return new CorrelationHint(
                row, "Transformation", "A" + row, dir,
                sourceTopic, sourceClass, sourceAttr,
                targetTopic, targetClass, targetAttr,
                targetTopic + "." + targetClass + "." + targetAttr,
                null, code, null, null,
                confidence, List.of());
    }

    private static MappingCandidate candidate(String id, Direction dir,
                                               String sourceClass, String sourceAttr,
                                               String targetClass, String targetAttr,
                                               String classification, double confidence, String origin) {
        return new MappingCandidate(
                id, dir, sourceClass, sourceAttr, targetClass, targetAttr,
                "${s." + sourceAttr + "}", "K", confidence,
                classification, origin, List.of());
    }
}
