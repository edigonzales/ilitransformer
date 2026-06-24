package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapRuleCoverageSummary(
        String ruleId,
        String targetOutput,
        String targetClass,
        List<IlimapCoverageAttributeSummary> attributes,
        List<IlimapSourceUsageSummary> sources,
        List<String> refs,
        int directAssignmentCount,
        int bagAssignmentCount,
        int line,
        int character) {

    public IlimapRuleCoverageSummary {
        attributes = List.copyOf(attributes);
        sources = List.copyOf(sources);
        refs = List.copyOf(refs);
    }
}
