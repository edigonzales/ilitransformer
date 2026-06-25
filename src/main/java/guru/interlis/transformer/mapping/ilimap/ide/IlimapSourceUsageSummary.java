package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapSourceUsageSummary(
        String alias,
        List<String> inputIds,
        String sourceClass,
        List<String> usedAttributes,
        List<String> usedRoles,
        int line,
        int character,
        String nodeId,
        IlimapOverviewLocation location) {

    public IlimapSourceUsageSummary {
        inputIds = List.copyOf(inputIds);
        usedAttributes = List.copyOf(usedAttributes);
        usedRoles = List.copyOf(usedRoles);
    }
}
