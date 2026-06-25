package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapCoverageClassSummary(
        String outputId,
        String className,
        boolean targeted,
        List<String> ruleIds,
        int attributeCount,
        int assignedAttributeCount,
        int mandatoryMissingCount,
        int line,
        int character,
        String nodeId,
        IlimapOverviewLocation location) {

    public IlimapCoverageClassSummary {
        ruleIds = List.copyOf(ruleIds);
    }
}
