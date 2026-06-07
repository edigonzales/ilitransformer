package guru.interlis.transformer.mapping.plan;

import java.util.List;

public record BagPlan(
        String bagAttrName,
        SourcePlan fromSource,
        String structureName,
        List<AssignmentPlan> assignments,
        String whereExpression,
        BagMode mode
) {
    public enum BagMode {
        EMBED,
        EXPAND
    }
}
