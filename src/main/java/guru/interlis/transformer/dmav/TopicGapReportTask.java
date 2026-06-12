package guru.interlis.transformer.dmav;

import guru.interlis.transformer.model.ModelInventory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TopicGapReportTask {

    public static void main(String[] args) throws Exception {
        Path hintsPath = Dm01DmavPaths.GENERATED_HINTS;
        Path candidatesPath = Dm01DmavPaths.GENERATED_CANDIDATES;
        Path reportPath = Dm01DmavPaths.TOPIC_GAP_REPORT;
        String dm01Model = "DM01AVCH24LV95D";
        String dm01Dir = Dm01DmavPaths.LOCAL_MODEL_DIR;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--hints" -> hintsPath = Path.of(args[++i]);
                case "--candidates" -> candidatesPath = Path.of(args[++i]);
                case "--report" -> reportPath = Path.of(args[++i]);
                case "--dm01-model" -> dm01Model = args[++i];
                case "--dm01-dir" -> dm01Dir = args[++i];
            }
        }

        if (!hintsPath.toFile().exists()) {
            System.err.println("Run importCorrelation first: hints file not found: " + hintsPath);
            System.exit(1);
        }
        if (!candidatesPath.toFile().exists()) {
            System.err.println("Run generateMappingCandidates first: candidates file not found: " + candidatesPath);
            System.exit(1);
        }

        System.out.println("Generating topic gap report...");
        System.out.println("  Hints:     " + hintsPath);
        System.out.println("  Candidates: " + candidatesPath);

        List<CorrelationHint> hints = TopicGapReportGenerator.loadHints(hintsPath);
        List<MappingCandidate> candidates = TopicGapReportGenerator.loadCandidates(candidatesPath);

        ModelInventory dm01Inventory = TopicGapReportGenerator.loadDm01Inventory(dm01Model, dm01Dir);

        Map<String, String> dmavModelDirs = new LinkedHashMap<>();
        dmavModelDirs.put("DMAV_FixpunkteAVKategorie3_V1_1", dm01Dir);
        dmavModelDirs.put("DMAV_Grundstuecke_V1_1", dm01Dir);
        dmavModelDirs.put("DMAV_Bodenbedeckung_V1_1", dm01Dir);
        dmavModelDirs.put("DMAV_Einzelobjekte_V1_1", dm01Dir);
        dmavModelDirs.put("DMAV_Nomenklatur_V1_1", dm01Dir);
        dmavModelDirs.put("DMAV_Gebaeudeadressen_V1_1", dm01Dir);
        dmavModelDirs.put("DMAV_Rohrleitungen_V1_1", dm01Dir);
        dmavModelDirs.put("DMAV_Toleranzstufen_V1_1", dm01Dir);

        Map<String, ModelInventory> dmavInventories = new LinkedHashMap<>();
        for (var entry : dmavModelDirs.entrySet()) {
            ModelInventory inv = TopicGapReportGenerator.loadDm01Inventory(
                    entry.getKey(), entry.getValue());
            if (inv != null) {
                dmavInventories.put(entry.getKey(), inv);
            }
        }

        TopicGapReportGenerator generator = new TopicGapReportGenerator(
                hints, candidates, dmavInventories, dm01Inventory, Map.of());

        TopicGapReportGenerator.GapReport report = generator.generate();
        generator.writeReport(report, reportPath);

        System.out.println("Report:  " + reportPath);
        System.out.println("Topics:  " + report.topicAnalyses().size());
        System.out.println("  with hints:      " + report.summary().topicsWithHints());
        System.out.println("  with candidates: " + report.summary().topicsWithCandidates());
        System.out.println("  high risk:       " + report.summary().highRiskTopicCount());
        System.out.println("Recommended slices: " + String.join(", ", report.recommendedSlices()));
    }
}
