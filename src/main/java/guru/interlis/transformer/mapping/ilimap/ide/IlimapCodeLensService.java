package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IlimapCodeLensService {

    public static final String SHOW_IN_OVERVIEW_COMMAND = "ilimap.showRuleInOverview";
    public static final String SHOW_COVERAGE_COMMAND = "ilimap.showRuleCoverage";

    private final IlimapMappingSummaryService summaryService;

    public IlimapCodeLensService() {
        this(new IlimapMappingSummaryService());
    }

    public IlimapCodeLensService(IlimapMappingSummaryService summaryService) {
        this.summaryService = Objects.requireNonNull(summaryService, "summaryService");
    }

    public List<IlimapCodeLensSummary> codeLenses(IlimapAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        IlimapMappingSummary summary = summaryService.summarize(analysis);
        if (!summary.available()) {
            return List.of();
        }

        Map<String, IlimapRuleCoverageSummary> coverageById = new LinkedHashMap<>();
        for (IlimapRuleCoverageSummary coverage : summary.ruleCoverage()) {
            coverageById.put(coverage.ruleId(), coverage);
        }

        List<IlimapCodeLensSummary> lenses = new ArrayList<>();
        for (IlimapRuleSummary rule : summary.rules()) {
            IlimapOverviewLocation location = rule.location();
            if (location == null || location.line() < 0 || location.character() < 0) {
                continue;
            }
            lenses.add(new IlimapCodeLensSummary(location, "Show in Overview", SHOW_IN_OVERVIEW_COMMAND, rule.id()));
            lenses.add(new IlimapCodeLensSummary(
                    location,
                    coverageTitle(rule, coverageById.get(rule.id()), summary),
                    SHOW_COVERAGE_COMMAND,
                    rule.id()));
        }
        return List.copyOf(lenses);
    }

    private static String coverageTitle(
            IlimapRuleSummary rule, IlimapRuleCoverageSummary coverage, IlimapMappingSummary summary) {
        List<String> segments = new ArrayList<>();
        if (coverage != null && !coverage.attributes().isEmpty()) {
            long assigned = coverage.attributes().stream()
                    .filter(IlimapCoverageAttributeSummary::assigned)
                    .count();
            segments.add("Coverage " + assigned + "/" + coverage.attributes().size());
        }
        if (rule.refCount() > 0) {
            segments.add(rule.refCount() + (rule.refCount() == 1 ? " ref" : " refs"));
        }
        if (rule.bagCount() > 0) {
            segments.add(rule.bagCount() + (rule.bagCount() == 1 ? " bag" : " bags"));
        }
        int errors = countDiagnostics(summary, rule.id(), "error");
        if (errors > 0) {
            segments.add(errors + (errors == 1 ? " error" : " errors"));
        }
        int warnings = countDiagnostics(summary, rule.id(), "warning");
        if (warnings > 0) {
            segments.add(warnings + (warnings == 1 ? " warning" : " warnings"));
        }
        return segments.isEmpty() ? "Coverage" : String.join(" · ", segments);
    }

    private static int countDiagnostics(IlimapMappingSummary summary, String ruleId, String severity) {
        return (int) summary.diagnostics().stream()
                .filter(diagnostic -> ruleId.equals(diagnostic.ruleId()))
                .filter(diagnostic -> severity.equals(diagnostic.severity()))
                .count();
    }
}
