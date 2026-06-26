package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapAssignmentSummary(
        String targetAttribute,
        String expression,
        String kind,
        List<IlimapExpressionDependencySummary> dependencies,
        IlimapOverviewLocation location) {}
