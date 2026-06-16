package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class CompilerReport {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public void writeJson(TransformPlan plan, Path outputPath) throws IOException {
        JSON_MAPPER.writeValue(outputPath.toFile(), toReportMap(plan));
    }

    public String toJson(TransformPlan plan) throws IOException {
        return JSON_MAPPER.writeValueAsString(toReportMap(plan));
    }

    public void writeMarkdown(TransformPlan plan, Path outputPath) throws IOException {
        Files.writeString(outputPath, toMarkdown(plan));
    }

    public String toMarkdown(TransformPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Compiler Report\n\n");

        if (plan.name() != null) {
            sb.append("**Job:** ").append(escape(plan.name())).append("\n\n");
        }
        if (plan.direction() != null) {
            sb.append("**Direction:** ").append(escape(plan.direction())).append("\n\n");
        }

        int errorCount = plan.diagnostics().all().stream()
                .filter(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                .toList()
                .size();
        int warnCount = plan.diagnostics().all().stream()
                .filter(d -> d.severity() == guru.interlis.transformer.diag.Severity.WARNING)
                .toList()
                .size();

        sb.append("**Rules:** ")
                .append(plan.rules().size())
                .append(" | **Errors:** ")
                .append(errorCount)
                .append(" | **Warnings:** ")
                .append(warnCount)
                .append("\n\n");

        if (!plan.diagnostics().all().isEmpty()) {
            sb.append("## Diagnostics\n\n");
            sb.append("| Severity | Code | Message | Rule | Suggestion |\n");
            sb.append("|----------|------|---------|------|------------|\n");
            for (Diagnostic d : plan.diagnostics().all()) {
                sb.append("| ")
                        .append(d.severity())
                        .append(" | ")
                        .append(escape(d.code()))
                        .append(" | ")
                        .append(escape(d.message()))
                        .append(" | ")
                        .append(escape(d.sourcePath() != null ? d.sourcePath() : "-"))
                        .append(" | ")
                        .append(escape(d.suggestion() != null ? d.suggestion() : "-"))
                        .append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("## Rules\n\n");
        for (RulePlan rp : plan.rules()) {
            sb.append("### ").append(escape(rp.ruleId())).append("\n\n");
            sb.append("- **Target:** ")
                    .append(escape(rp.targetClass() != null ? rp.targetClass().getName() : "?"))
                    .append(" → ")
                    .append(escape(rp.outputId()))
                    .append("\n");
            sb.append("- **Sources:** ");
            for (int i = 0; i < rp.sources().size(); i++) {
                if (i > 0) sb.append(", ");
                var sp = rp.sources().get(i);
                sb.append("`")
                        .append(escape(sp.alias()))
                        .append("`: ")
                        .append(escape(
                                sp.sourceClass() != null ? sp.sourceClass().getName() : "?"));
            }
            sb.append("\n");

            if (!rp.assignments().isEmpty()) {
                sb.append("\n**Assignments:**\n\n");
                sb.append("| Target | Expression | Result Type |\n");
                sb.append("|--------|------------|-------------|\n");
                for (AssignmentPlan ap : rp.assignments()) {
                    sb.append("| ")
                            .append(escape(ap.targetAttrName()))
                            .append(" | ")
                            .append(escape(ap.expression().sourceText()))
                            .append(" | ")
                            .append(ap.expression().resultType())
                            .append(" |\n");
                }
            }

            if (!rp.refs().isEmpty()) {
                sb.append("\n**References:**\n\n");
                sb.append("| Role | Association | Source Ref | Target Rule | Required |\n");
                sb.append("|------|-------------|------------|-------------|----------|\n");
                for (RefPlan ref : rp.refs()) {
                    sb.append("| ")
                            .append(escape(ref.targetRoleName() != null ? ref.targetRoleName() : "-"))
                            .append(" | ")
                            .append(escape(ref.association() != null ? ref.association() : "-"))
                            .append(" | ")
                            .append(escape(ref.sourceRef() != null ? ref.sourceRef() : "-"))
                            .append(" | ")
                            .append(escape(ref.targetRuleId() != null ? ref.targetRuleId() : "-"))
                            .append(" | ")
                            .append(ref.required() ? "yes" : "no")
                            .append(" |\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private Map<String, Object> toReportMap(TransformPlan plan) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("name", plan.name());
        report.put("direction", plan.direction());
        report.put("failPolicy", plan.failPolicy().name().toLowerCase());

        List<Map<String, Object>> ruleMaps = new ArrayList<>();
        for (RulePlan rp : plan.rules()) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("ruleId", rp.ruleId());
            rm.put("outputId", rp.outputId());
            rm.put("targetClass", rp.targetClass() != null ? rp.targetClass().getName() : null);

            List<Map<String, Object>> srcMaps = new ArrayList<>();
            for (var sp : rp.sources()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("alias", sp.alias());
                sm.put(
                        "sourceClass",
                        sp.sourceClass() != null ? sp.sourceClass().getName() : null);
                sm.put("inputIds", sp.inputIds());
                srcMaps.add(sm);
            }
            rm.put("sources", srcMaps);

            List<Map<String, Object>> assignMaps = new ArrayList<>();
            for (var ap : rp.assignments()) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("target", ap.targetAttrName());
                am.put("expression", ap.expression().sourceText());
                am.put("resultType", ap.expression().resultType().name());
                assignMaps.add(am);
            }
            rm.put("assignments", assignMaps);

            List<Map<String, Object>> refMaps = new ArrayList<>();
            for (var ref : rp.refs()) {
                Map<String, Object> rf = new LinkedHashMap<>();
                rf.put("role", ref.targetRoleName());
                rf.put("association", ref.association());
                rf.put("sourceRef", ref.sourceRef());
                rf.put("targetRuleId", ref.targetRuleId());
                rf.put("required", ref.required());
                refMaps.add(rf);
            }
            rm.put("refs", refMaps);
            ruleMaps.add(rm);
        }
        report.put("rules", ruleMaps);

        List<Map<String, Object>> diagMaps = new ArrayList<>();
        for (Diagnostic d : plan.diagnostics().all()) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("severity", d.severity().name());
            dm.put("code", d.code());
            dm.put("message", d.message());
            dm.put("ruleId", d.sourcePath());
            dm.put("suggestion", d.suggestion());
            diagMaps.add(dm);
        }
        report.put("diagnostics", diagMaps);

        return report;
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
