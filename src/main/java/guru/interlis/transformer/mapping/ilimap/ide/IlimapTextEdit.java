package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapTextEdit(IlimapIdeRange range, String newText) {

    public IlimapTextEdit {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(newText, "newText");
    }
}
