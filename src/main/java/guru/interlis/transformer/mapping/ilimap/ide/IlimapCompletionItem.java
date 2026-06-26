package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapCompletionItem(
        String label,
        IlimapCompletionKind kind,
        String detail,
        String documentation,
        String insertText,
        IlimapIdeRange replacementRange,
        boolean snippet,
        String filterText) {

    public IlimapCompletionItem(
            String label, IlimapCompletionKind kind, String detail, String documentation, String insertText) {
        this(label, kind, detail, documentation, insertText, null, false, null);
    }

    public IlimapCompletionItem(
            String label,
            IlimapCompletionKind kind,
            String detail,
            String documentation,
            String insertText,
            IlimapIdeRange replacementRange) {
        this(label, kind, detail, documentation, insertText, replacementRange, false, null);
    }

    public IlimapCompletionItem(
            String label,
            IlimapCompletionKind kind,
            String detail,
            String documentation,
            String insertText,
            IlimapIdeRange replacementRange,
            boolean snippet) {
        this(label, kind, detail, documentation, insertText, replacementRange, snippet, null);
    }

    public static IlimapCompletionItem snippet(
            String label, String detail, String documentation, String insertText, IlimapIdeRange replacementRange) {
        return new IlimapCompletionItem(
                label, IlimapCompletionKind.SNIPPET, detail, documentation, insertText, replacementRange, true, null);
    }

    public IlimapCompletionItem {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(insertText, "insertText");
    }
}
