package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;
import java.util.Objects;

public record IlimapCodeAction(String title, String kind, List<IlimapTextEdit> edits, String diagnosticCode) {

    public IlimapCodeAction {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(edits, "edits");
        edits = List.copyOf(edits);
    }
}
