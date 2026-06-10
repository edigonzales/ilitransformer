package guru.interlis.transformer.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        List<String> lfp3RelatedClasses
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
