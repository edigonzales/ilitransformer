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
            validateJoinSpecs(rule, diagnostics);
            validateCreateSpecs(rule, diagnostics);
            validateBagModes(rule, diagnostics);
        }
        validateBasketStrategy(config, diagnostics);
    }

    private void validateJoinSpecs(JobConfig.RuleSpec rule, DiagnosticCollector diagnostics) {
        if (rule.joins == null || rule.joins.isEmpty()) {
            return;
        }
        java.util.Set<String> sourceAliases = new java.util.HashSet<>();
        for (JobConfig.SourceSpec s : rule.sources) {
            if (s.alias != null && !s.alias.isBlank()) {
                sourceAliases.add(s.alias);
            }
        }
        for (JobConfig.JoinSpec join : rule.joins) {
            if (join.left == null || join.left.isBlank()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_JOIN_INVALID,
                        Severity.ERROR,
                        "Join missing 'left' alias. Rule: " + rule.id,
                        rule.id,
                        "Specify the left source alias for the join"));
                continue;
            }
            if (!sourceAliases.contains(join.left)) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_JOIN_UNKNOWN_ALIAS,
                        Severity.ERROR,
                        "Join 'left' alias '" + join.left + "' not found in rule sources. Rule: " + rule.id,
                        rule.id,
                        "Check that the alias is defined in the rule's sources"));
            }
            if (join.right == null || join.right.isBlank()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_JOIN_INVALID,
                        Severity.ERROR,
                        "Join missing 'right' alias. Rule: " + rule.id,
                        rule.id,
                        "Specify the right source alias for the join"));
                continue;
            }
            if (!sourceAliases.contains(join.right)) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_JOIN_UNKNOWN_ALIAS,
                        Severity.ERROR,
                        "Join 'right' alias '" + join.right + "' not found in rule sources. Rule: " + rule.id,
                        rule.id,
                        "Check that the alias is defined in the rule's sources"));
            }
            if (join.left.equals(join.right)) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_JOIN_SELF_REF,
                        Severity.ERROR,
                        "Join 'left' and 'right' aliases are the same: " + join.left + ". Rule: " + rule.id,
                        rule.id,
                        "Joins require two different source aliases"));
            }
            if (join.on == null || join.on.isBlank()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_JOIN_INVALID,
                        Severity.ERROR,
                        "Join missing 'on' condition. Rule: " + rule.id,
                        rule.id,
                        "Specify an equi-join condition like 'left.attr = right.attr'"));
            }
            if (join.type != null
                    && !join.type.isBlank()
                    && !join.type.equalsIgnoreCase("inner")
                    && !join.type.equalsIgnoreCase("left")) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_JOIN_INVALID,
                        Severity.ERROR,
                        "Unknown join type '" + join.type + "'. Rule: " + rule.id,
                        rule.id,
                        "Valid types: inner, left"));
            }
        }
    }

    private void validateCreateSpecs(JobConfig.RuleSpec rule, DiagnosticCollector diagnostics) {
        if (rule.create == null || rule.create.isEmpty()) {
            return;
        }
        java.util.Set<String> createIds = new java.util.HashSet<>();
        for (JobConfig.CreateSpec create : rule.create) {
            if (create.clazz == null || create.clazz.isBlank()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_CREATE_INVALID,
                        Severity.ERROR,
                        "Create missing target class. Rule: " + rule.id,
                        rule.id,
                        "Specify the target class for the create directive"));
            }
            String id = create.clazz;
            if (!createIds.add(id)) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_CREATE_DUPLICATE,
                        Severity.ERROR,
                        "Duplicate create target class '" + id + "'. Rule: " + rule.id,
                        rule.id,
                        "Each create directive must target a unique class"));
            }
        }
    }

    private void validateBagModes(JobConfig.RuleSpec rule, DiagnosticCollector diagnostics) {
        if (rule.bags != null) {
            for (var entry : rule.bags.entrySet()) {
                String bagMode = entry.getValue().mode;
                if (bagMode != null && !bagMode.isBlank()) {
                    try {
                        BagPlan.BagMode.valueOf(bagMode.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        diagnostics.add(new Diagnostic(
                                DiagnosticCode.MAP_UNSUPPORTED_BAG_MODE,
                                Severity.ERROR,
                                "Unknown BAG mode '" + bagMode + "' in rule " + rule.id + ", bag " + entry.getKey(),
                                rule.id,
                                "Valid modes: EMBED, EXPAND"));
                    }
                }
            }
        }
    }

    private void validateBasketStrategy(JobConfig config, DiagnosticCollector diagnostics) {
        if (config.mapping.basketStrategy != null
                && config.mapping.basketStrategy.defaultStrategy != null
                && config.mapping.basketStrategy.defaultStrategy.equalsIgnoreCase("expression")) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.MAP_UNSUPPORTED_BASKET_STRATEGY,
                    Severity.ERROR,
                    "Basket strategy 'expression' is not yet supported",
                    null,
                    "Use one of: preserve, generateUuid, preserveOrGenerateUuid, byTopic"));
        }
    }
}
