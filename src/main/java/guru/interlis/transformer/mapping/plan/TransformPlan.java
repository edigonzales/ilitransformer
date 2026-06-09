package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.diag.DiagnosticCollector;

public record TransformPlan(
        String name,
        String direction,
        FailPolicy failPolicy,
        CompileMode compileMode,
        java.util.List<RulePlan> rules,
        java.util.Map<String, InputBinding> inputsById,
        java.util.Map<String, OutputBinding> outputsById,
        DiagnosticCollector diagnostics,
        OidPlan oidPlan,
        BasketPlan basketPlan,
        java.util.Map<String, java.util.Map<String, String>> enumMaps
) {

    public boolean isReportOnly() {
        return compileMode == CompileMode.REPORT;
    }
}
