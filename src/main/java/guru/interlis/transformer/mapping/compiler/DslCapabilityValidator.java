package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.BagPlan;

public final class DslCapabilityValidator {

    public void validateSupportedFeatures(JobConfig config, DiagnosticCollector diagnostics) {
        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            validateRuleFeatures(rule, diagnostics);
        }
        validateBasketStrategy(config, diagnostics);
    }

    private void validateRuleFeatures(JobConfig.RuleSpec rule, DiagnosticCollector diagnostics) {
        if (rule.joins != null && !rule.joins.isEmpty()) {
            diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNSUPPORTED_FEATURE, Severity.ERROR,
                    "Joins are not yet supported (Phase 22). Rule: " + rule.id,
                    rule.id, "Remove joins definition"));
        }
        if (rule.create != null && !rule.create.isEmpty()) {
            diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNSUPPORTED_FEATURE, Severity.ERROR,
                    "Create is not yet supported (Phase 22). Rule: " + rule.id,
                    rule.id, "Remove create definition"));
        }
        if (rule.bags != null) {
            for (var entry : rule.bags.entrySet()) {
                String bagMode = entry.getValue().mode;
                if (bagMode != null && !bagMode.isBlank()) {
                    try {
                        BagPlan.BagMode.valueOf(bagMode.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNSUPPORTED_BAG_MODE, Severity.ERROR,
                                "Unknown BAG mode '" + bagMode + "' in rule " + rule.id + ", bag " + entry.getKey(),
                                rule.id, "Valid modes: EMBED, EXPAND"));
                    }
                }
            }
        }
    }

    private void validateBasketStrategy(JobConfig config, DiagnosticCollector diagnostics) {
        if (config.mapping.basketStrategy != null
                && config.mapping.basketStrategy.defaultStrategy != null
                && config.mapping.basketStrategy.defaultStrategy.equalsIgnoreCase("expression")) {
            diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNSUPPORTED_BASKET_STRATEGY, Severity.ERROR,
                    "Basket strategy 'expression' is not yet supported",
                    null, "Use one of: preserve, generateUuid, preserveOrGenerateUuid, byTopic"));
        }
    }
}
