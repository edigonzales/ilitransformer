package guru.interlis.transformer.mapping.plan;

import java.util.List;

public record BagPlan(
        String bagAttrName,
        SourcePlan fromSource,
        String structureName,
        List<AssignmentPlan> assignments,
        CompiledExpression whereExpression,
        BagMode mode,
        String parentRefAttribute,
        String parentAlias,
        Integer cardinalityMin,
        Integer cardinalityMax,
        Integer maxItems,
        IdentityPlan identityPlan,
        RefPlan parentRefPlan
) {
    public enum BagMode {
        EMBED,
        EXPAND
    }

    public boolean hasParentRef() {
        return parentRefAttribute != null && !parentRefAttribute.isBlank()
                && parentAlias != null && !parentAlias.isBlank();
    }

    public boolean hasIdentityPlan() {
        return identityPlan != null && identityPlan.oidStrategy() != null;
    }

    public boolean isEmbed() {
        return mode == BagMode.EMBED;
    }

    public boolean isExpand() {
        return mode == BagMode.EXPAND;
    }
}
