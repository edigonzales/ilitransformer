package guru.interlis.transformer.dmav;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class CorrelationHintExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeJson(List<CorrelationHint> hints, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        List<Map<String, Object>> data = hints.stream().map(this::toMap).collect(Collectors.toList());
        MAPPER.writeValue(outputPath.toFile(), data);
    }

    public void writeReport(CorrelationWorkbookImporter.ImportResult result, Path reportPath) throws IOException {
        Files.createDirectories(reportPath.getParent());
        StringBuilder md = new StringBuilder();
        List<CorrelationHint> hints = result.hints();

        md.append("# DM01↔DMAV Correlation Import Report\n\n");
        md.append("## Summary\n\n");
        md.append("| Metric | Count |\n");
        md.append("|---|---|\n");
        md.append("| Total hints | ").append(hints.size()).append(" |\n");

        long dmToDmav = hints.stream()
                .filter(h -> h.direction() == Direction.DM01_TO_DMAV)
                .count();
        long dmavToDm = hints.stream()
                .filter(h -> h.direction() == Direction.DMAV_TO_DM01)
                .count();
        md.append("| DM01→DMAV | ").append(dmToDmav).append(" |\n");
        md.append("| DMAV→DM01 | ").append(dmavToDm).append(" |\n");

        md.append("\n## By Transform Code\n\n");
        md.append("| Code | DM01→DMAV | DMAV→DM01 | Total |\n");
        md.append("|---|---|---|---|\n");
        for (String code : List.of("K", "V", "I")) {
            long dm = hints.stream()
                    .filter(h -> h.direction() == Direction.DM01_TO_DMAV && code.equals(h.transformCode()))
                    .count();
            long md2 = hints.stream()
                    .filter(h -> h.direction() == Direction.DMAV_TO_DM01 && code.equals(h.transformCode()))
                    .count();
            md.append("| ")
                    .append(code)
                    .append(" | ")
                    .append(dm)
                    .append(" | ")
                    .append(md2)
                    .append(" | ")
                    .append(dm + md2)
                    .append(" |\n");
        }

        md.append("\n## Diagnostics\n\n");
        md.append("| Severity | Count |\n");
        md.append("|---|---|\n");
        md.append("| Errors | ").append(result.errorCount()).append(" |\n");
        md.append("| Warnings | ").append(result.warningCount()).append(" |\n");

        if (result.warningCount() > 0) {
            md.append("\n### Warnings\n\n");
            result.diagnostics().all().stream()
                    .filter(d -> d.severity().name().equals("WARNING"))
                    .forEach(d -> md.append("- ")
                            .append(d.message())
                            .append(" (")
                            .append(d.sourcePath())
                            .append(")\n"));
        }

        md.append("\n## Source\n\n");
        md.append("- XLSX: `docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx`\n");
        md.append("- Sheet: `Transformation`\n");
        md.append("- Generated: `").append(reportPath).append("`\n");

        Files.writeString(reportPath, md.toString());
    }

    private Map<String, Object> toMap(CorrelationHint h) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("row", h.rowNumber());
        map.put("sheet", h.sheetName());
        map.put("cell", h.cellPosition());
        map.put("direction", h.direction().name());
        map.put("sourceTopic", h.sourceTopic());
        map.put("sourceClass", h.sourceClass());
        map.put("sourceAttribute", h.sourceAttribute());
        map.put("targetTopic", h.targetTopic());
        map.put("targetClass", h.targetClass());
        map.put("targetAttribute", h.targetAttribute());
        map.put("targetPath", h.targetPath());
        map.put("condition", h.conditionText());
        map.put("transformCode", h.transformCode());
        map.put("addition", h.additionText());
        map.put("comment", h.comment());
        map.put("confidence", h.confidence());
        if (!h.warnings().isEmpty()) {
            map.put("warnings", h.warnings());
        }
        return map;
    }
}
