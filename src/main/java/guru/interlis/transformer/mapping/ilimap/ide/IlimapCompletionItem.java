package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapCompletionItem(
        String label,
        IlimapCompletionKind kind,
        String detail,
        String documentation,
        String insertText,
        IlimapIdeRange replacementRange,
        boolean snippet) {

    public IlimapCompletionItem(
            String label, IlimapCompletionKind kind, String detail, String documentation, String insertText) {
        this(label, kind, detail, documentation, insertText, null, false);
    }

    public IlimapCompletionItem(
            String label,
            IlimapCompletionKind kind,
            String detail,
            String documentation,
            String insertText,
            IlimapIdeRange replacementRange) {
        this(label, kind, detail, documentation, insertText, replacementRange, false);
    }

    public static IlimapCompletionItem snippet(
            String label, String detail, String documentation, String insertText, IlimapIdeRange replacementRange) {
        return new IlimapCompletionItem(
                label, IlimapCompletionKind.SNIPPET, detail, documentation, insertText, replacementRange, true);
    }

    public IlimapCompletionItem {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(insertText, "insertText");
    }
}
