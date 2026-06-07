package guru.interlis.transformer.mapping.plan;

import ch.interlis.ili2c.metamodel.Table;

public record RulePlan(
        String ruleId,
        String outputId,
        Table targetClass,
        java.util.List<SourcePlan> sources,
        java.util.List<AssignmentPlan> assignments,
        java.util.List<RefPlan> refs
) {}
