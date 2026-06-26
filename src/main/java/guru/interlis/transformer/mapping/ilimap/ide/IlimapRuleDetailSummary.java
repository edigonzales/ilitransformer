package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapRuleDetailSummary(
        boolean available,
        String message,
        String ruleId,
        String nodeId,
        IlimapOverviewLocation location,
        IlimapTargetDetailSummary target,
        List<IlimapSourceDetailSummary> sources,
        List<IlimapJoinSummary> joins,
        List<IlimapExpressionSummary> identity,
        List<IlimapAssignmentSummary> assignments,
        List<IlimapAssignmentSummary> defaults,
        List<IlimapBagSummary> bags,
        List<IlimapRefSummary> refs,
        List<IlimapLossSummary> losses,
        IlimapMetadataSummary metadata,
        List<IlimapDiagnosticSummary> diagnostics) {

    public static IlimapRuleDetailSummary unavailable(String ruleId, String message) {
        return new IlimapRuleDetailSummary(
                false,
                message,
                ruleId != null ? ruleId : "",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of());
    }
}
