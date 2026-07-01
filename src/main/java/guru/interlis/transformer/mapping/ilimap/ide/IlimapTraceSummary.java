package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;
import java.util.Objects;

public record IlimapTraceSummary(
        boolean available,
        String message,
        String mode,
        String ruleId,
        IlimapTraceTarget target,
        IlimapTraceExpression expression,
        List<IlimapTraceDependency> dependencies,
        List<IlimapTraceUsage> usages,
        List<IlimapTraceStep> steps,
        List<IlimapDiagnosticSummary> diagnostics) {

    public IlimapTraceSummary {
        Objects.requireNonNull(message, "message");
        dependencies = List.copyOf(dependencies);
        usages = List.copyOf(usages);
        steps = List.copyOf(steps);
        diagnostics = List.copyOf(diagnostics);
    }

    public static IlimapTraceSummary unavailable(String mode, String message) {
        return new IlimapTraceSummary(
                false,
                message,
                mode,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
