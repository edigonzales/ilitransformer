package guru.interlis.transformer.mapping.plan;

import ch.interlis.ili2c.metamodel.Table;

public record CreatePlan(
        String id,
        String outputId,
        Table targetClass,
        java.util.Optional<CompiledExpression> predicate,
        java.util.List<AssignmentPlan> assignments,
        java.util.List<RefPlan> references,
        java.util.List<BagPlan> bags,
        IdentityPlan identity) {}
