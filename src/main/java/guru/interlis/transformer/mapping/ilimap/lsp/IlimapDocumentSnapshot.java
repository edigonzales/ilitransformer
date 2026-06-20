package guru.interlis.transformer.mapping.ilimap.lsp;

import java.util.Objects;

record IlimapDocumentSnapshot(String uri, String text, int version) {

    IlimapDocumentSnapshot {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(text, "text");
    }
}
