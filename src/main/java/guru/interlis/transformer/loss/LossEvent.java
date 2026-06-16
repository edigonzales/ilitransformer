package guru.interlis.transformer.loss;

public record LossEvent(
        String ruleId,
        String sourceClass,
        String sourceOid,
        String sourcePath,
        String reasonCode,
        String description) {}
