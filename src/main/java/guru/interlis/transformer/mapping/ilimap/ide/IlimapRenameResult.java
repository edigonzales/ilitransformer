package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapRenameResult(boolean available, String message, List<IlimapTextEdit> edits) {

    public IlimapRenameResult {
        if (edits == null) {
            edits = List.of();
        }
    }

    public static IlimapRenameResult unavailable(String message) {
        return new IlimapRenameResult(false, message, List.of());
    }
}
