package guru.interlis.transformer.app;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.ExecutionMetricsSnapshot;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class TransformationReportWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public void writeJson(
            Path outputPath,
            TransformPlan plan,
            TransformResult result,
            DiagnosticCollector diagnostics,
            List<ValidationResult> validations,
            Duration elapsed,
            Map<String, String> modelVersions,
            ExecutionMetricsSnapshot metricsSnapshot,
            RunProfileSnapshot runProfile)
            throws IOException {
        Files.createDirectories(outputPath.getParent());
        JSON_MAPPER.writeValue(
                outputPath.toFile(),
                buildReportMap(
                        plan, result, diagnostics, validations, elapsed, modelVersions, metricsSnapshot, runProfile));
    }

    public void writeMarkdown(
            Path outputPath,
            TransformPlan plan,
            TransformResult result,
            DiagnosticCollector diagnostics,
            List<ValidationResult> validations,
            Duration elapsed,
            Map<String, String> modelVersions,
            ExecutionMetricsSnapshot metricsSnapshot,
            RunProfileSnapshot runProfile)
            throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.writeString(
                outputPath,
                buildMarkdown(
                        plan, result, diagnostics, validations, elapsed, modelVersions, metricsSnapshot, runProfile));
    }

    // -- JSON ---------------------------------------------------------------

    private Map<String, Object> buildReportMap(
            TransformPlan plan,
            TransformResult result,
            DiagnosticCollector diagnostics,
            List<ValidationResult> validations,
            Duration elapsed,
            Map<String, String> modelVersions,
            ExecutionMetricsSnapshot metricsSnapshot,
            RunProfileSnapshot runProfile) {

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("name", plan.name());
        report.put("direction", plan.direction());
        report.put("failPolicy", plan.failPolicy().name().toLowerCase());
        report.put("elapsedMs", elapsed.toMillis());
        if (runProfile != null) {
            Map<String, Object> run = new LinkedHashMap<>();
            run.put("compilePrepareMs", runProfile.compilePrepareMs());
            run.put("validationMs", runProfile.validationMs());
            run.put("reportWriteMs", runProfile.reportWriteMs());
            run.put("totalRunMs", runProfile.totalRunMs());
            report.put("runProfile", run);
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("sourceRecordsRead", result.sourceRecordsRead());
        counts.put("sourceRecordsFiltered", result.sourceRecordsFiltered());
        counts.put("targetsCreated", result.targetsCreated());
        counts.put("targetsWritten", result.targetsWritten());
        counts.put("errors", result.errors());
        counts.put("warnings", result.warnings());
        report.put("counts", counts);

        report.put("rules", plan.rules().size());
        report.put("inputs", plan.inputsById().size());
        report.put("outputs", plan.outputsById().size());

        if (metricsSnapshot != null && metricsSnapshot.elapsedMillis() > 0) {
            Map<String, Object> perf = new LinkedHashMap<>();
            perf.put("elapsedMs", metricsSnapshot.elapsedMillis());
            perf.put("sourceIndexMs", metricsSnapshot.sourceIndexMs());
            perf.put("ruleExecutionMs", metricsSnapshot.ruleExecutionMs());
            perf.put("referenceResolutionMs", metricsSnapshot.referenceResolutionMs());
            perf.put("outputWriteMs", metricsSnapshot.outputWriteMs());
            perf.put("joinLookups", metricsSnapshot.joinLookups());
            perf.put("bagLookups", metricsSnapshot.bagLookups());
            perf.put("ruleMatches", metricsSnapshot.ruleMatches());
            perf.put("targetsByClass", metricsSnapshot.targetsByClass());
            if (!metricsSnapshot.rules().isEmpty()) {
                List<Map<String, Object>> ruleMaps = new ArrayList<>();
                for (var rule : metricsSnapshot.rules()) {
                    Map<String, Object> rm = new LinkedHashMap<>();
                    rm.put("ruleId", rule.ruleId());
                    rm.put("sourceRecordsVisited", rule.sourceRecordsVisited());
                    rm.put("matches", rule.matches());
                    rm.put("filtered", rule.filtered());
                    rm.put("targetsCreated", rule.targetsCreated());
                    rm.put("elapsedMs", rule.elapsedMillis());
                    ruleMaps.add(rm);
                }
                perf.put("rules", ruleMaps);
            }
            report.put("performance", perf);
        }

        List<Map<String, Object>> diagMaps = new ArrayList<>();
        for (Diagnostic d : diagnostics.all()) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("severity", d.severity().name());
            dm.put("code", d.code());
            dm.put("message", d.message());
            dm.put("ruleId", d.sourcePath());
            dm.put("suggestion", d.suggestion());
            diagMaps.add(dm);
        }
        report.put("diagnostics", diagMaps);

        if (!validations.isEmpty()) {
            List<Map<String, Object>> valMaps = new ArrayList<>();
            for (var vr : validations) {
                Map<String, Object> vm = new LinkedHashMap<>();
                vm.put("valid", vr.valid());
                vm.put("errorCount", vr.errorCount());
                vm.put("warningCount", vr.warningCount());
                if (vr.logFile() != null) {
                    vm.put("logFile", vr.logFile().toString());
                }
                valMaps.add(vm);
            }
            report.put("validations", valMaps);
        }

        if (modelVersions != null && !modelVersions.isEmpty()) {
            report.put("models", modelVersions);
        }

        return report;
    }

    // -- Markdown -----------------------------------------------------------

    private String buildMarkdown(
            TransformPlan plan,
            TransformResult result,
            DiagnosticCollector diagnostics,
            List<ValidationResult> validations,
            Duration elapsed,
            Map<String, String> modelVersions,
            ExecutionMetricsSnapshot metricsSnapshot,
            RunProfileSnapshot runProfile) {

        StringBuilder sb = new StringBuilder();
        sb.append("# Transformation Report\n\n");

        sb.append("**Job:** ").append(escape(plan.name())).append("\n\n");
        sb.append("**Direction:** ").append(escape(plan.direction())).append("\n\n");
        sb.append("**Fail Policy:** ")
                .append(plan.failPolicy().name().toLowerCase())
                .append("\n\n");
        sb.append("**Elapsed:** ").append(formatDuration(elapsed)).append("\n\n");

        if (runProfile != null) {
            sb.append("## Run Profile\n\n");
            sb.append("| Metric | Value |\n");
            sb.append("|--------|-------|\n");
            sb.append("| Compile/prepare (ms) | ")
                    .append(runProfile.compilePrepareMs())
                    .append(" |\n");
            sb.append("| Validation (ms) | ").append(runProfile.validationMs()).append(" |\n");
            sb.append("| Report write (ms) | ")
                    .append(runProfile.reportWriteMs())
                    .append(" |\n");
            sb.append("| Total run (ms) | ").append(runProfile.totalRunMs()).append(" |\n\n");
        }

        sb.append("## Counts\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Source records read | ").append(result.sourceRecordsRead()).append(" |\n");
        sb.append("| Source records filtered | ")
                .append(result.sourceRecordsFiltered())
                .append(" |\n");
        sb.append("| Targets created | ").append(result.targetsCreated()).append(" |\n");
        sb.append("| Targets written | ").append(result.targetsWritten()).append(" |\n");
        sb.append("| Errors | ").append(result.errors()).append(" |\n");
        sb.append("| Warnings | ").append(result.warnings()).append(" |\n");
        sb.append("| Rules | ").append(plan.rules().size()).append(" |\n");
        sb.append("| Inputs | ").append(plan.inputsById().size()).append(" |\n");
        sb.append("| Outputs | ").append(plan.outputsById().size()).append(" |\n\n");

        if (metricsSnapshot != null && metricsSnapshot.elapsedMillis() > 0) {
            sb.append("## Performance\n\n");
            sb.append("| Metric | Value |\n");
            sb.append("|--------|-------|\n");
            sb.append("| Elapsed (ms) | ")
                    .append(metricsSnapshot.elapsedMillis())
                    .append(" |\n");
            sb.append("| Source index (ms) | ")
                    .append(metricsSnapshot.sourceIndexMs())
                    .append(" |\n");
            sb.append("| Rule execution (ms) | ")
                    .append(metricsSnapshot.ruleExecutionMs())
                    .append(" |\n");
            sb.append("| Reference resolution (ms) | ")
                    .append(metricsSnapshot.referenceResolutionMs())
                    .append(" |\n");
            sb.append("| Output write (ms) | ")
                    .append(metricsSnapshot.outputWriteMs())
                    .append(" |\n");
            sb.append("| Join lookups | ").append(metricsSnapshot.joinLookups()).append(" |\n");
            sb.append("| BAG lookups | ").append(metricsSnapshot.bagLookups()).append(" |\n");
            sb.append("| Rule matches | ").append(metricsSnapshot.ruleMatches()).append(" |\n");
            if (!metricsSnapshot.targetsByClass().isEmpty()) {
                sb.append("| Targets by class | |\n");
                for (var entry : metricsSnapshot.targetsByClass().entrySet()) {
                    sb.append("|   ")
                            .append(escape(entry.getKey()))
                            .append(" | ")
                            .append(entry.getValue())
                            .append(" |\n");
                }
            }
            sb.append("\n");
            if (!metricsSnapshot.rules().isEmpty()) {
                sb.append("### Rules\n\n");
                sb.append("| Rule | Visited | Matches | Filtered | Targets | Elapsed (ms) |\n");
                sb.append("|------|---------|---------|----------|---------|--------------|\n");
                for (var rule : metricsSnapshot.rules()) {
                    sb.append("| ")
                            .append(escape(rule.ruleId()))
                            .append(" | ")
                            .append(rule.sourceRecordsVisited())
                            .append(" | ")
                            .append(rule.matches())
                            .append(" | ")
                            .append(rule.filtered())
                            .append(" | ")
                            .append(rule.targetsCreated())
                            .append(" | ")
                            .append(rule.elapsedMillis())
                            .append(" |\n");
                }
                sb.append("\n");
            }
        }

        if (!diagnostics.all().isEmpty()) {
            sb.append("## Diagnostics\n\n");
            sb.append("| Severity | Code | Message | Rule | Suggestion |\n");
            sb.append("|----------|------|---------|------|------------|\n");
            for (Diagnostic d : diagnostics.all()) {
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

        if (!validations.isEmpty()) {
            sb.append("## Validation\n\n");
            sb.append("| File | Valid | Errors | Warnings | Log |\n");
            sb.append("|------|-------|--------|----------|-----|\n");
            for (var vr : validations) {
                sb.append("| ")
                        .append(vr.logFile() != null ? vr.logFile().getFileName() : "-")
                        .append(" | ")
                        .append(vr.valid() ? "yes" : "no")
                        .append(" | ")
                        .append(vr.errorCount())
                        .append(" | ")
                        .append(vr.warningCount())
                        .append(" | ")
                        .append(vr.logFile() != null ? vr.logFile().toString() : "-")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        if (modelVersions != null && !modelVersions.isEmpty()) {
            sb.append("## Models\n\n");
            for (var entry : modelVersions.entrySet()) {
                sb.append("- **")
                        .append(escape(entry.getKey()))
                        .append("**: ")
                        .append(escape(entry.getValue()))
                        .append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String formatDuration(Duration duration) {
        long ms = duration.toMillis();
        long s = ms / 1000;
        long m = s / 60;
        s = s % 60;
        ms = ms % 1000;
        if (m > 0) {
            return String.format("%dm %ds %dms", m, s, ms);
        } else if (s > 0) {
            return String.format("%ds %dms", s, ms);
        }
        return ms + "ms";
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
