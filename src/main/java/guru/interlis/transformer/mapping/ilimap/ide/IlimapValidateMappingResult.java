package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapValidateMappingResult(boolean available, String message, int diagnosticCount) {

    public IlimapValidateMappingResult {
        Objects.requireNonNull(message, "message");
    }

    public static IlimapValidateMappingResult unavailable(String message) {
        return new IlimapValidateMappingResult(false, message, 0);
    }

    public static IlimapValidateMappingResult available(int diagnosticCount) {
        return new IlimapValidateMappingResult(true, "", diagnosticCount);
    }
}
