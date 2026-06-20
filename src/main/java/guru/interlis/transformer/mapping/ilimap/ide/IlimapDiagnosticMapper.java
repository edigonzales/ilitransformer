package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.Severity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapDiagnosticMapper {

    private static final Pattern LINE_COLUMN_PATTERN =
            Pattern.compile("(?i).*line\\s+(\\d+)\\s*,\\s*column\\s+(\\d+).*");
    private static final Pattern TRAILING_LINE_COLUMN_PATTERN = Pattern.compile("(?:^|.*:)(\\d+):(\\d+)$");

    public List<IlimapIdeDiagnostic> map(
            List<Diagnostic> diagnostics, IlimapLineMap lineMap, IlimapIdeRange fallbackRange) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        return diagnostics.stream()
                .map(diagnostic -> map(diagnostic, lineMap, fallbackRange))
                .toList();
    }

    public IlimapIdeDiagnostic map(Diagnostic diagnostic, IlimapLineMap lineMap, IlimapIdeRange fallbackRange) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        Objects.requireNonNull(lineMap, "lineMap");
        Objects.requireNonNull(fallbackRange, "fallbackRange");

        return new IlimapIdeDiagnostic(
                diagnostic.code(),
                mapSeverity(diagnostic.severity()),
                diagnostic.message(),
                extractRange(diagnostic.sourcePath(), lineMap).orElse(fallbackRange),
                diagnostic.suggestion());
    }

    private Optional<IlimapIdeRange> extractRange(String sourcePath, IlimapLineMap lineMap) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return Optional.empty();
        }

        Matcher lineColumn = LINE_COLUMN_PATTERN.matcher(sourcePath);
        if (lineColumn.matches()) {
            return Optional.of(toOneCharacterRange(
                    Integer.parseInt(lineColumn.group(1)) - 1,
                    Integer.parseInt(lineColumn.group(2)) - 1,
                    lineMap));
        }

        Matcher trailingLineColumn = TRAILING_LINE_COLUMN_PATTERN.matcher(sourcePath);
        if (trailingLineColumn.matches()) {
            return Optional.of(toOneCharacterRange(
                    Integer.parseInt(trailingLineColumn.group(1)) - 1,
                    Integer.parseInt(trailingLineColumn.group(2)) - 1,
                    lineMap));
        }

        return Optional.empty();
    }

    private IlimapIdeRange toOneCharacterRange(int zeroBasedLine, int zeroBasedCharacter, IlimapLineMap lineMap) {
        int line = Math.max(0, zeroBasedLine);
        int character = Math.max(0, zeroBasedCharacter);
        int startOffset = lineMap.positionToOffset(line, character);
        int endOffset = lineMap.positionToOffset(line, character + 1);
        return new IlimapIdeRange(lineMap.toIdePosition(startOffset), lineMap.toIdePosition(endOffset));
    }

    private IlimapIdeSeverity mapSeverity(Severity severity) {
        return switch (severity) {
            case ERROR -> IlimapIdeSeverity.ERROR;
            case WARNING -> IlimapIdeSeverity.WARNING;
            case INFO -> IlimapIdeSeverity.INFORMATION;
        };
    }
}
