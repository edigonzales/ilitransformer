package guru.interlis.transformer.dmav;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.ModelInventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TopicGapReportGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<CorrelationHint> hints;
    private final List<MappingCandidate> candidates;
    private final Map<String, ModelInventory> dmavInventories;
    private final ModelInventory dm01Inventory;
    private final Map<String, IliModelCompileResult> compileResults;

    public TopicGapReportGenerator(
            List<CorrelationHint> hints,
            List<MappingCandidate> candidates,
            Map<String, ModelInventory> dmavInventories,
            ModelInventory dm01Inventory,
            Map<String, IliModelCompileResult> compileResults) {
        this.hints = List.copyOf(hints);
        this.candidates = List.copyOf(candidates);
        this.dmavInventories = Map.copyOf(dmavInventories);
        this.dm01Inventory = dm01Inventory;
        this.compileResults = compileResults;
    }

    public record GapReport(
            Map<String, TopicAnalysis> topicAnalyses,
            List<String> highRiskTopics,
            List<String> recommendedSlices,
            List<String> openIssues,
            SummaryStats summary
    ) {}

    public record TopicAnalysis(
            String dm01Topic,
            String dmavModel,
            int hintCount,
            int candidateCount,
            String complexity,
            List<String> riskFlags,
            List<String> dm01Classes,
            List<String> dmavClasses,
            List<String> openIssues,
            String pilotStatus
    ) {}

    public record SummaryStats(
            int totalDm01Topics,
            int totalDmavModels,
            int topicsWithHints,
            int topicsWithCandidates,
            int highRiskTopicCount,
            long totalHints,
            long totalCandidates
    ) {}

    public GapReport generate() {
        Map<String, TopicAnalysis> analyses = new LinkedHashMap<>();
        List<String> highRiskTopics = new ArrayList<>();
        List<String> recommendedSlices = new ArrayList<>();

        // Group candidates by DM01 topic
        Map<String, List<MappingCandidate>> byTopic = candidates.stream()
                .collect(Collectors.groupingBy(c -> extractTopic(c.sourceClass()),
                        LinkedHashMap::new, Collectors.toList()));

        for (var entry : byTopic.entrySet()) {
            String topicName = entry.getKey();
            List<MappingCandidate> topicCandidates = entry.getValue();

            // Count hints for this topic
            long hintCount = hints.stream()
                    .filter(h -> h.direction() == Direction.DM01_TO_DMAV
                            && (topicName.equals(h.sourceTopic())
                                || (h.sourceClass() != null && h.sourceClass().contains(topicName))))
                    .count();

            // Find matching DMAV model
            String dmavModel = findDmavModel(topicCandidates);

            // Compute complexity
            ComplexityResult complexity = computeComplexity(topicName, topicCandidates, dmavModel);

            // Dm01 classes
            List<String> dm01Classes = topicCandidates.stream()
                    .map(MappingCandidate::sourceClass)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            // Dmav classes
            List<String> dmavClasses = topicCandidates.stream()
                    .map(MappingCandidate::targetClass)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            // Topic-specific open issues
            List<String> topicOpenIssues = collectTopicOpenIssues(topicName, dmavModel);

            // Pilot status
            String pilotStatus = "FixpunkteKategorie3".equals(topicName)
                    || topicName.contains("Fixpunkt") ? "**PILOT IMPLEMENTED** (LFP3)" : "not started";

            TopicAnalysis analysis = new TopicAnalysis(
                    topicName, dmavModel != null ? dmavModel : "unknown",
                    (int) hintCount, topicCandidates.size(),
                    complexity.complexityLabel(), complexity.flags(),
                    dm01Classes, dmavClasses, topicOpenIssues, pilotStatus);

            analyses.put(topicName, analysis);

            if (complexity.isHighRisk()) {
                highRiskTopics.add(topicName);
            }
        }

        // Add topics with zero candidates (from hints or DM01 inventory)
        for (String topic : collectAllDm01Topics()) {
            if (!analyses.containsKey(topic)) {
                long hintCount = hints.stream()
                        .filter(h -> h.sourceTopic() != null && h.sourceTopic().equals(topic))
                        .count();
                TopicAnalysis analysis = new TopicAnalysis(
                        topic, "unknown", (int) hintCount, 0,
                        "manual", List.of("⚪ No candidates generated"),
                        List.of(), List.of(), List.of(), "not started");
                analyses.put(topic, analysis);
            }
        }

        // Recommend next slices
        recommendedSlices = recommendSlices(analyses);

        // Collect cross-cutting open issues
        List<String> openIssues = collectOpenIssues(analyses);

        SummaryStats summary = new SummaryStats(
                collectAllDm01Topics().size(),
                0,
                (int) analyses.values().stream().filter(a -> a.hintCount() > 0).count(),
                (int) analyses.values().stream().filter(a -> a.candidateCount() > 0).count(),
                highRiskTopics.size(),
                hints.size(),
                candidates.size());

        return new GapReport(analyses, highRiskTopics, recommendedSlices, openIssues, summary);
    }

    public void writeReport(GapReport report, Path reportPath) throws IOException {
        Files.createDirectories(reportPath.getParent());
        StringBuilder md = new StringBuilder();

        // Header
        md.append("# DM01 ↔ DMAV Topic Gap Report\n\n");
        md.append("> Generated: ").append(java.time.LocalDateTime.now()).append("\n\n");

        // Summary
        SummaryStats s = report.summary();
        md.append("## 1. Summary\n\n");
        md.append("| Metric | Count |\n");
        md.append("|---|---|\n");
        md.append("| DM01 topics | ").append(s.totalDm01Topics()).append(" |\n");
        md.append("| Topics with XLSX hints | ").append(s.topicsWithHints()).append(" |\n");
        md.append("| Topics with generated candidates | ").append(s.topicsWithCandidates()).append(" |\n");
        md.append("| High-risk topics | ").append(s.highRiskTopicCount()).append(" |\n");
        md.append("| Total XLSX hints | ").append(s.totalHints()).append(" |\n");
        md.append("| Total candidates | ").append(s.totalCandidates()).append(" |\n");

        // Complexity overview
        md.append("\n### Complexity Distribution\n\n");
        md.append("| Complexity | Count | Topics |\n");
        md.append("|---|---|---|\n");
        for (String level : List.of("sehr schwierig", "schwierig", "mittel", "einfach", "manual")) {
            List<String> topics = report.topicAnalyses().entrySet().stream()
                    .filter(e -> e.getValue().complexity().equals(level))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.toList());
            if (!topics.isEmpty()) {
                md.append("| **").append(level).append("** | ").append(topics.size())
                        .append(" | ").append(String.join(", ", topics)).append(" |\n");
            }
        }

        // Topic Analysis
        md.append("\n## 2. Topic Analysis\n\n");
        for (var entry : report.topicAnalyses().entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = complexityOrder(b.getValue().complexity())
                            - complexityOrder(a.getValue().complexity());
                    if (cmp != 0) return cmp;
                    return a.getKey().compareTo(b.getKey());
                }).toList()) {
            TopicAnalysis a = entry.getValue();
            md.append("### 2.").append(sectionNumber(entry.getKey(), report))
                    .append(" ").append(entry.getKey());

            if (!a.pilotStatus().equals("not started")) {
                md.append(" — ").append(a.pilotStatus());
            }
            md.append("\n\n");

            md.append("| Property | Value |\n");
            md.append("|---|---|\n");
            md.append("| Complexity | **").append(a.complexity()).append("** |\n");
            md.append("| DMAV Model | ").append(a.dmavModel()).append(" |\n");
            md.append("| Hints | ").append(a.hintCount()).append(" |\n");
            md.append("| Candidates | ").append(a.candidateCount()).append(" |\n");

            if (!a.riskFlags().isEmpty()) {
                md.append("| Risk Flags | ").append(String.join("<br>", a.riskFlags())).append(" |\n");
            }

            if (!a.dm01Classes().isEmpty()) {
                md.append("\n**DM01 Classes:** ");
                md.append(a.dm01Classes().stream().map(c -> "`" + shortName(c) + "`")
                        .collect(Collectors.joining(", ")));
                md.append("\n");
            }

            if (!a.dmavClasses().isEmpty()) {
                md.append("\n**DMAV Classes:** ");
                md.append(a.dmavClasses().stream().map(c -> "`" + shortName(c) + "`")
                        .collect(Collectors.joining(", ")));
                md.append("\n");
            }

            if (!a.openIssues().isEmpty()) {
                md.append("\n**Open Issues:**\n");
                for (String issue : a.openIssues()) {
                    md.append("- ").append(issue).append("\n");
                }
            }
            md.append("\n---\n\n");
        }

        // Cross-cutting concerns
        md.append("## 3. Cross-Cutting Concerns\n\n");
        addCrossCuttingSection(md, report);

        // Recommended next slices
        md.append("## 4. Recommended Next Slices\n\n");
        if (report.recommendedSlices().isEmpty()) {
            md.append("No clear recommendations — manual review needed.\n\n");
        } else {
            int i = 1;
            for (String slice : report.recommendedSlices()) {
                md.append(i++).append(". ").append(slice).append("\n");
            }
            md.append("\n");
        }

        // Open questions summary
        md.append("## 5. Open Questions Summary\n\n");
        md.append("> See also: `docs/dm01-dmav/open-questions.md`\n\n");
        if (report.openIssues().isEmpty()) {
            md.append("No cross-cutting open issues identified.\n\n");
        } else {
            for (String issue : report.openIssues()) {
                md.append("- ").append(issue).append("\n");
            }
            md.append("\n");
        }

        // Source
        md.append("## 6. Source\n\n");
        md.append("- XLSX Correlation Table: `docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx`\n");
        md.append("- Hints JSON: `build/generated/dm01-dmav/correlation-hints.json`\n");
        md.append("- Candidates JSON: `build/generated/dm01-dmav/mapping-candidates.json`\n");
        md.append("- Report: `").append(reportPath).append("`\n");

        Files.writeString(reportPath, md.toString());
    }

    private ComplexityResult computeComplexity(String topic, List<MappingCandidate> candidates, String dmavModel) {
        double score = 0;
        List<String> flags = new ArrayList<>();

        // Check AREA/SURFACE attributes in the topic
        boolean hasArea = false;
        boolean hasSurface = false;
        boolean hasLineattr = false;
        boolean hasBag = false;
        boolean hasAssociation = false;

        for (MappingCandidate c : candidates) {
            String expr = c.expression() != null ? c.expression() : "";
            String target = c.targetAttribute() != null ? c.targetAttribute().toLowerCase() : "";
            String source = c.sourceAttribute() != null ? c.sourceAttribute().toLowerCase() : "";

            if (target.contains("flaeche") || target.contains("area") || source.contains("area")
                    || c.targetAttribute() != null && c.targetAttribute().endsWith("_Geometrie")) {
                hasArea = true;
            }
            if (target.contains("surface") || target.contains("perimeter")) {
                hasSurface = true;
            }
            if (source.contains("linienart") || target.contains("linienart")
                    || source.contains("lineattr") || target.contains("lineattr")) {
                hasLineattr = true;
            }
            if (c.targetAttribute() != null && c.targetAttribute().contains("BAG")) {
                hasBag = true;
            }
        }

        // Check hints for additional flags
        for (CorrelationHint h : hints) {
            String st = h.sourceTopic() != null ? h.sourceTopic() : "";
            if (st.equals(topic) || (h.sourceClass() != null && h.sourceClass().contains(topic))) {
                String cond = h.conditionText() != null ? h.conditionText().toLowerCase() : "";
                String note = h.additionText() != null ? h.additionText().toLowerCase() : "";
                if (cond.contains("lineattr") || note.contains("lineattr")
                        || cond.contains("linienart") || note.contains("linienart")) {
                    hasLineattr = true;
                }
            }
        }

        // Check DM01 inventory for associations
        if (dm01Inventory != null) {
            for (ModelInventory.TopicInventory t : dm01Inventory.topics()) {
                if (t.name().equals(topic)) {
                    for (ModelInventory.ClassInventory cls : t.classes()) {
                        if (!cls.roles().isEmpty()) {
                            hasAssociation = true;
                        }
                        for (ModelInventory.AttributeInventory attr : cls.attributes()) {
                            String typeStr = attr.typeString() != null ? attr.typeString() : "";
                            if (typeStr.contains("AREA") || typeStr.contains("COORD")) {
                                hasArea = true;
                            }
                        }
                    }
                }
            }
        }

        // Score accumulation
        if (hasArea) { score += 1.5; flags.add("🔴 AREA geometry present"); }
        if (hasSurface) { score += 0.5; flags.add("🟡 SURFACE geometry present"); }
        if (hasLineattr) { score += 1.0; flags.add("🔴 LINEATTR present"); }
        if (hasBag) { score += 0.5; flags.add("🟡 BAG OF STRUCTURE"); }
        if (hasAssociation && candidates.size() > 5) { score += 0.5; flags.add("🟡 Multiple associations"); }

        // Low confidence penalty
        long lowConf = candidates.stream().filter(c -> c.confidence() < 0.5).count();
        if (lowConf > candidates.size() / 2) {
            score += 1.0;
            flags.add("🔴 Most candidates low confidence (< 0.5)");
        }

        // No hints means very high risk
        long hintedCands = candidates.stream().filter(c -> "XLSX".equals(c.origin())).count();
        if (hintedCands == 0 && candidates.size() > 0) {
            score += 1.0;
            flags.add("🟡 No XLSX hints — synonym-based only");
        }

        String complexity;
        if (score >= 3.0) complexity = "sehr schwierig";
        else if (score >= 2.0) complexity = "schwierig";
        else if (score >= 1.0) complexity = "mittel";
        else if (candidates.size() > 0) complexity = "einfach";
        else complexity = "manual";

        return new ComplexityResult(complexity, score >= 2.0, flags);
    }

    private record ComplexityResult(String complexityLabel, boolean isHighRisk, List<String> flags) {}

    private int complexityOrder(String complexity) {
        return switch (complexity) {
            case "sehr schwierig" -> 4;
            case "schwierig" -> 3;
            case "mittel" -> 2;
            case "einfach" -> 1;
            default -> 0;
        };
    }

    private String findDmavModel(List<MappingCandidate> candidates) {
        return candidates.stream()
                .map(c -> extractModel(c.targetClass()))
                .filter(m -> m != null && !m.isBlank())
                .findFirst().orElse("unknown");
    }

    private List<String> collectAllDm01Topics() {
        List<String> topics = new ArrayList<>();
        // From inventory
        if (dm01Inventory != null) {
            for (var topic : dm01Inventory.topics()) {
                topics.add(topic.name());
            }
        }
        // From hints
        for (CorrelationHint h : hints) {
            if (h.sourceTopic() != null && !topics.contains(h.sourceTopic())) {
                topics.add(h.sourceTopic());
            }
        }
        // From candidates
        for (MappingCandidate c : candidates) {
            String topic = extractTopic(c.sourceClass());
            if (topic != null && !topics.contains(topic)) {
                topics.add(topic);
            }
        }
        return topics;
    }

    private List<String> collectTopicOpenIssues(String topic, String dmavModel) {
        List<String> issues = new ArrayList<>();

        // Geometry concerns
        if ("Bodenbedeckung".equals(topic) || "Liegenschaften".equals(topic)
                || "Gemeindegrenzen".equals(topic)) {
            issues.add("AREA vs SURFACE conversion: see §31.4 Q1");
            issues.add("Topology repair vs validation: see §31.4 Q2");
        }
        if ("Liegenschaften".equals(topic) || "Gemeindegrenzen".equals(topic)
                || "Rohrleitungen".equals(topic)) {
            issues.add("LINEATTR handling: see §31.4 Q3");
        }

        // Point deduplication
        if (topic.toLowerCase().contains("fixpunkt")) {
            issues.add("Point deduplication: LFP3 ↔ Grenzpunkt ↔ Hoheitsgrenzpunkt: see §31.2 Q6");
        }

        // OID type
        if (dmavModel != null && dmavModel.contains("UUIDOID") && compileResults != null) {
            for (var cr : compileResults.entrySet()) {
                if (cr.getKey().equals(dmavModel)) {
                    issues.add("OID type mismatch: DM01 STANDARDOID ↔ DMAV UUIDOID: see §31.3 Q4");
                    break;
                }
            }
        }

        return issues;
    }

    private List<String> recommendSlices(Map<String, TopicAnalysis> analyses) {
        List<String> recommendations = new ArrayList<>();

        // Easy topics first
        for (var entry : analyses.entrySet()) {
            if (entry.getValue().complexity().equals("einfach")
                    && !entry.getKey().equals("FixpunkteKategorie3")) {
                recommendations.add("**" + entry.getKey() + "** (einfach) — " + entry.getValue().candidateCount() + " candidates");
            }
        }

        // Medium topics
        for (var entry : analyses.entrySet()) {
            if (entry.getValue().complexity().equals("mittel")) {
                recommendations.add("**" + entry.getKey() + "** (mittel) — " + entry.getValue().candidateCount() + " candidates");
            }
        }

        // If no easy/medium, note that
        if (recommendations.isEmpty()) {
            recommendations.add("No clear low-complexity slices — manual review of high-risk topics is needed first");
        }

        // Limit to 3
        if (recommendations.size() > 3) {
            return recommendations.subList(0, 3);
        }
        return recommendations;
    }

    private List<String> collectOpenIssues(Map<String, TopicAnalysis> analyses) {
        List<String> issues = new ArrayList<>();
        issues.add("**AREA vs SURFACE**: " + countTopicsWithFlag(analyses, "AREA") + " topics affected — see §31.4 Q1");
        issues.add("**LINEATTR**: " + countTopicsWithFlag(analyses, "LINEATTR") + " topics affected — see §31.4 Q3");
        issues.add("**Default values**: GueltigerEintrag, Protokoll, AktiverUnterhalt, Grenzpunktfunktion — see §31.2 Q1-Q5");
        issues.add("**Reverse direction**: DMAV→DM01 lossiness — see §31.3");
        issues.add("**ilivalidator integration**: library vs external process — see §31.5 Q1");
        return issues;
    }

    private String countTopicsWithFlag(Map<String, TopicAnalysis> analyses, String flag) {
        long count = analyses.values().stream()
                .filter(a -> a.riskFlags().stream().anyMatch(f -> f.contains(flag)))
                .count();
        return count + "/" + analyses.size();
    }

    private void addCrossCuttingSection(StringBuilder md, GapReport report) {
        md.append("### 3.1 Geometry Types\n\n");
        md.append("| Type | Topics Affected | Status |\n");
        md.append("|---|---|---|\n");
        md.append("| AREA | ").append(countAffected(report, "AREA")).append(" | Phase 14+: deferred, passthrough in Phase 13 |\n");
        md.append("| SURFACE | ").append(countAffected(report, "SURFACE")).append(" | Phase 14+: deferred |\n");
        md.append("| LINEATTR | ").append(countAffected(report, "LINEATTR")).append(" | Phase 14+: GEOM-LINEATTR-UNSUPPORTED diagnostic |\n");
        md.append("| COORD/POLYLINE | All | Phase 13: passthrough implemented |\n");

        md.append("\n### 3.2 Data Model Mismatches\n\n");
        md.append("| Issue | Impact |\n");
        md.append("|---|---|\n");
        md.append("| STANDARDOID ↔ UUIDOID | All DMAV targets |\n");
        md.append("| ITF geometry splitting | AREA/SURFACE in DM01 |\n");
        md.append("| INTERLIS 1 → INTERLIS 2 | All DM01 → DMAV |\n");
        md.append("| Point deduplication | Fixpunkte ↔ Grenzpunkte |\n");
    }

    private String countAffected(GapReport report, String flag) {
        long count = report.topicAnalyses().values().stream()
                .filter(a -> a.riskFlags().stream().anyMatch(f -> f.contains(flag)))
                .count();
        return count + " topics";
    }

    private static String extractTopic(String qualifiedClass) {
        if (qualifiedClass == null) return "unknown";
        String[] parts = qualifiedClass.split("\\.");
        if (parts.length >= 2) return parts[parts.length - 2];
        return qualifiedClass;
    }

    private static String extractModel(String qualifiedClass) {
        if (qualifiedClass == null) return null;
        String[] parts = qualifiedClass.split("\\.");
        if (parts.length >= 3) return parts[0];
        return null;
    }

    private static String shortName(String qualifiedName) {
        if (qualifiedName == null) return "?";
        String[] parts = qualifiedName.split("\\.");
        return parts[parts.length - 1];
    }

    private static String sectionNumber(String key, GapReport report) {
        int index = 0;
        for (String k : report.topicAnalyses().keySet()) {
            index++;
            if (k.equals(key)) return String.valueOf(index);
        }
        return "?";
    }

    // Static helpers for loading data

    public static List<CorrelationHint> loadHints(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), new TypeReference<>() {});
    }

    public static List<MappingCandidate> loadCandidates(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), new TypeReference<>() {});
    }

    public static ModelInventory loadDm01Inventory(String modelName, String modelDir) {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(modelName, modelDir);
        if (result.hasErrors() || result.transferDescription() == null) return null;
        return service.buildInventory(result.transferDescription(), modelName);
    }

    public static Map<String, ModelInventory> loadDmavInventories(
            Map<String, String> dmavModelDirs) {
        Map<String, ModelInventory> inventories = new LinkedHashMap<>();
        IliModelService service = new IliModelService();
        for (var entry : dmavModelDirs.entrySet()) {
            String modelName = entry.getKey();
            String modelDir = entry.getValue();
            IliModelCompileResult result = service.compileModel(modelName, modelDir);
            if (!result.hasErrors() && result.transferDescription() != null) {
                ModelInventory inv = service.buildInventory(result.transferDescription(), modelName);
                inventories.put(modelName, inv);
            }
        }
        return inventories;
    }
}
