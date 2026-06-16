package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class StructuralValidator {

    private final IdentityCompiler identityCompiler = new IdentityCompiler();

    void validateVersion(JobConfig config, DiagnosticCollector diag) {
        if (config.version < 1) {
            diag.add(new Diagnostic(
                    DiagnosticCode.MAP_VERSION,
                    Severity.ERROR,
                    "Mapping file must declare 'version: 1' or higher",
                    null,
                    "Add 'version: 1' at the top of the mapping YAML"
            ));
        }
    }

    void validateOutputs(JobConfig config, DiagnosticCollector diag) {
        for (JobConfig.OutputSpec output : config.job.outputs) {
            if (output.id == null || output.id.isBlank()) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_MISSING_ID,
                        Severity.ERROR,
                        "Output id is required",
                        null,
                        "Add an 'id' to each output in job.outputs"
                ));
            }
        }
    }

    void validateRules(JobConfig config, CompilerContext ctx) {
        validateRulesStructurally(config, ctx);
    }

    void validateRulesStructurally(JobConfig config, CompilerContext ctx) {
        DiagnosticCollector diag = ctx.diagnostics();
        Set<String> ruleIds = new HashSet<>();
        Set<String> outputIds = new HashSet<>();
        for (JobConfig.OutputSpec o : config.job.outputs) {
            if (o.id != null && !o.id.isBlank()) outputIds.add(o.id);
        }
        Set<String> inputIds = new HashSet<>();
        for (JobConfig.InputSpec i : config.job.inputs) {
            if (i.id != null && !i.id.isBlank()) inputIds.add(i.id);
        }

        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            validateRuleId(rule, ruleIds, diag);
            validateRuleTarget(rule, outputIds, diag);
            validateRuleSources(rule, inputIds, outputIds, diag);
            identityCompiler.validateIdentityKeysStructurally(rule, ctx);
        }
    }

    private void validateRuleId(JobConfig.RuleSpec rule, Set<String> seenIds, DiagnosticCollector diag) {
        if (rule.id == null || rule.id.isBlank()) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_ID, Severity.ERROR,
                    "Rule is missing required 'id' field",
                    rule.getEffectiveTargetClass(),
                    "Add an 'id' to each rule"));
        } else if (!seenIds.add(rule.id)) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_ID, Severity.ERROR,
                    "Duplicate rule id: " + rule.id,
                    rule.getEffectiveTargetClass(),
                    "Rule ids must be unique within a mapping file"));
        }
    }

    private void validateRuleTarget(JobConfig.RuleSpec rule, Set<String> outputIds, DiagnosticCollector diag) {
        String targetClass = rule.getEffectiveTargetClass();
        if (targetClass == null || targetClass.isBlank()) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_TARGET_CLASS, Severity.ERROR,
                    "Rule is missing target class",
                    rule.id,
                    "Add 'target.class' or 'targetClass' to the rule"));
        }
        String targetOutput = rule.getEffectiveTargetOutput();
        if (targetOutput != null && !targetOutput.isBlank() && !outputIds.isEmpty()
                && !outputIds.contains(targetOutput)) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_OUTPUT, Severity.ERROR,
                    "Rule references unknown output: " + targetOutput,
                    rule.id,
                    "Ensure the output id exists in job.outputs"));
        }
    }

    private void validateRuleSources(JobConfig.RuleSpec rule, Set<String> inputIds,
                                      Set<String> outputIds, DiagnosticCollector diag) {
        Set<String> aliases = new HashSet<>();
        for (JobConfig.SourceSpec source : rule.sources) {
            if (source.alias == null || source.alias.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_ALIAS, Severity.ERROR,
                        "Source is missing required 'alias' field in rule " + rule.id,
                        rule.id, "Add an 'alias' to each source"));
                continue;
            }
            if (!aliases.add(source.alias)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_ALIAS, Severity.ERROR,
                        "Duplicate source alias '" + source.alias + "' in rule " + rule.id,
                        rule.id, "Source aliases must be unique within a rule"));
            }
            if (source.clazz == null || source.clazz.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_SOURCE_CLASS, Severity.ERROR,
                        "Source '" + source.alias + "' is missing 'class' field in rule " + rule.id,
                        rule.id, "Add 'class' to each source definition"));
            }
            List<String> sourceInputs = source.getInputIds();
            if (sourceInputs.isEmpty() || sourceInputs.stream().allMatch(String::isBlank)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_INPUT, Severity.ERROR,
                        "Source '" + source.alias + "' is missing 'input' field in rule " + rule.id,
                        rule.id, "Add 'input' or 'inputs' to each source definition"));
            }
            for (String inputId : sourceInputs) {
                if (inputId != null && !inputId.isBlank() && !inputIds.isEmpty()
                        && !inputIds.contains(inputId)) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_INPUT, Severity.ERROR,
                            "Source '" + source.alias + "' references unknown input: " + inputId,
                            rule.id, "Available inputs: " + inputIds));
                }
            }
        }
    }
}
