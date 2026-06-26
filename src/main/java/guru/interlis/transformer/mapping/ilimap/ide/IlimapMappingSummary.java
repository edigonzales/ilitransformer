package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;
import java.util.Objects;

public record IlimapMappingSummary(
        boolean available,
        String message,
        String mappingName,
        int inputCount,
        int outputCount,
        int ruleCount,
        int enumMapCount,
        int bagCount,
        int refCount,
        int errorCount,
        int warningCount,
        int informationCount,
        int hintCount,
        List<IlimapMappingInputSummary> inputs,
        List<IlimapMappingOutputSummary> outputs,
        List<IlimapEnumMapSummary> enumMaps,
        List<IlimapRuleSummary> rules,
        List<IlimapDiagnosticSummary> diagnostics,
        boolean coverageAvailable,
        String coverageMessage,
        List<IlimapCoverageClassSummary> classCoverage,
        List<IlimapRuleCoverageSummary> ruleCoverage,
        List<IlimapSourceClassUsageSummary> sourceUsage) {

    public IlimapMappingSummary {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(mappingName, "mappingName");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(enumMaps, "enumMaps");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(coverageMessage, "coverageMessage");
        Objects.requireNonNull(classCoverage, "classCoverage");
        Objects.requireNonNull(ruleCoverage, "ruleCoverage");
        Objects.requireNonNull(sourceUsage, "sourceUsage");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        enumMaps = List.copyOf(enumMaps);
        rules = List.copyOf(rules);
        diagnostics = List.copyOf(diagnostics);
        classCoverage = List.copyOf(classCoverage);
        ruleCoverage = List.copyOf(ruleCoverage);
        sourceUsage = List.copyOf(sourceUsage);
    }

    public static IlimapMappingSummary unavailable(String message) {
        return new IlimapMappingSummary(
                false, message, "mapping", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(),
                List.of(), false, message, List.of(), List.of(), List.of());
    }
}
