package guru.interlis.transformer.dmav;

import java.nio.file.Path;

public final class MappingCandidateTask {

    public static void main(String[] args) throws Exception {
        Path hints = null;
        Path synonyms = null;
        Path out = Dm01DmavPaths.GENERATED_CANDIDATES;
        Path report = Dm01DmavPaths.CANDIDATE_REPORT;
        Path yamlDm01Dmav = Dm01DmavPaths.GENERATED_DM01_TO_DMAV_YAML;
        Path yamlDmavDm01 = Dm01DmavPaths.GENERATED_DMAV_TO_DM01_YAML;
        String dm01Model = "DM01AVCH24LV95D";
        String dm01Dir = Dm01DmavPaths.LOCAL_MODEL_DIR;
        String dmavModel = null;
        String dmavDir = Dm01DmavPaths.LOCAL_MODEL_DIR;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--hints" -> hints = Path.of(args[++i]);
                case "--synonyms" -> synonyms = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
                case "--yaml-dm01-dmav" -> yamlDm01Dmav = Path.of(args[++i]);
                case "--yaml-dmav-dm01" -> yamlDmavDm01 = Path.of(args[++i]);
                case "--dm01-model" -> dm01Model = args[++i];
                case "--dm01-dir" -> dm01Dir = args[++i];
                case "--dmav-model" -> dmavModel = args[++i];
                case "--dmav-dir" -> dmavDir = args[++i];
            }
        }

        if (hints == null) {
            System.err.println("Usage: MappingCandidateTask --hints <path> [options]");
            System.exit(1);
        }

        System.out.println("Generating mapping candidates...");
        MappingCandidateGenerator generator = new MappingCandidateGenerator();
        MappingCandidateGenerator.GenerationResult result = generator.generate(
                hints, synonyms, dm01Model, dm01Dir, dmavModel, dmavDir);

        MappingCandidateExporter exporter = new MappingCandidateExporter();
        exporter.writeJson(result.candidates(), out);
        exporter.writeReport(result, report);

        if (dmavModel != null) {
            exporter.writeYaml(result.candidates(), Direction.DM01_TO_DMAV,
                    dm01Model, dmavModel, yamlDm01Dmav);
            exporter.writeYaml(result.candidates(), Direction.DMAV_TO_DM01,
                    dmavModel, dm01Model, yamlDmavDm01);
        }

        System.out.println("Generated " + result.candidates().size() + " mapping candidates");
        System.out.println("  high:   " + result.highCount());
        System.out.println("  medium: " + result.mediumCount());
        System.out.println("  low:    " + result.lowCount());
        System.out.println("  manual: " + result.manualCount());
        System.out.println("  DM01→DMAV: " + result.dm01ToDmavCount());
        System.out.println("  DMAV→DM01: " + result.dmavToDm01Count());

        for (String w : result.warnings()) {
            System.out.println("[WARN] " + w);
        }

        System.out.println("JSON:   " + out);
        System.out.println("Report: " + report);
    }
}
