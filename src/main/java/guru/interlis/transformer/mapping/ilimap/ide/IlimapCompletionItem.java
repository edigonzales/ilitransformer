package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapCompletionItem(
        String label, IlimapCompletionKind kind, String detail, String documentation, String insertText) {

    public IlimapCompletionItem {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(insertText, "insertText");
    }
}
