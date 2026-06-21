package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapCompletionItem(
        String label,
        IlimapCompletionKind kind,
        String detail,
        String documentation,
        String insertText,
        IlimapIdeRange replacementRange) {

    public IlimapCompletionItem(
            String label, IlimapCompletionKind kind, String detail, String documentation, String insertText) {
        this(label, kind, detail, documentation, insertText, null);
    }

    public IlimapCompletionItem {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(insertText, "insertText");
    }
}
