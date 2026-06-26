package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapSourceAttributeUsageSummary(
        String name,
        String kind,
        String status,
        List<IlimapUsageReferenceSummary> usedBy,
        IlimapOverviewLocation location) {

    public IlimapSourceAttributeUsageSummary {
        usedBy = List.copyOf(usedBy);
    }
}
