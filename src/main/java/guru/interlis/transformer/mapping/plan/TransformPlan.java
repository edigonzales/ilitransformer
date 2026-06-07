package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.model.TypeSystemFacade;

public record TransformPlan(
        String name,
        String direction,
        String failPolicy,
        java.util.List<RulePlan> rules,
        java.util.Map<String, TypeSystemFacade> sourceTypeSystems,
        java.util.Map<String, TypeSystemFacade> targetTypeSystems,
        DiagnosticCollector diagnostics
) {}
