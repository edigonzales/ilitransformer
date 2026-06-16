package guru.interlis.transformer.dmav;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CorrelationHint(
        @JsonAlias("row") int rowNumber,
        @JsonAlias("sheet") String sheetName,
        @JsonAlias("cell") String cellPosition,
        Direction direction,
        String sourceTopic,
        String sourceClass,
        String sourceAttribute,
        String targetTopic,
        String targetClass,
        String targetAttribute,
        String targetPath,
        @JsonAlias("condition") String conditionText,
        String transformCode,
        @JsonAlias("addition") String additionText,
        String comment,
        double confidence,
        List<String> warnings) {
    public CorrelationHint {
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
