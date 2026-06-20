package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapIdeDiagnostic(
        String code, IlimapIdeSeverity severity, String message, IlimapIdeRange range, String suggestion) {

    public IlimapIdeDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(range, "range");
    }
}
