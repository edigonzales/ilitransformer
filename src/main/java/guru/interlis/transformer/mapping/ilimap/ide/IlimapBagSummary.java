package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapBagSummary(
        String name,
        String targetAttribute,
        String structureClass,
        String mode,
        Integer maxItems,
        IlimapSourceDetailSummary source,
        List<IlimapAssignmentSummary> assignments,
        List<IlimapBagSummary> nestedBags,
        IlimapOverviewLocation location) {}
