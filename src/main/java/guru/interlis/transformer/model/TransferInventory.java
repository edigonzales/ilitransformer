package guru.interlis.transformer.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record TransferInventory(
        Path transferFile,
        String format,
        List<String> modelNames,
        long totalObjects,
        int basketCount,
        List<BasketSummary> baskets,
        List<ClassStats> classStats,
        List<String> geometryTypes,
        Map<String, Integer> referenceCounts,
        Map<String, List<String>> classifications
) {
    public record BasketSummary(
            String bid,
            String topic,
            long objectCount
    ) {}

    public record ClassStats(
            String className,
            long count,
            String oidType,
            List<String> geometryAttrs,
            boolean hasReferences
    ) {}
}
