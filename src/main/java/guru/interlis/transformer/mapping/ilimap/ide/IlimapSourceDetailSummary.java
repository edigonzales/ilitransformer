package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapSourceDetailSummary(
        String alias,
        List<String> inputIds,
        String className,
        String where,
        IlimapOverviewLocation location) {}
