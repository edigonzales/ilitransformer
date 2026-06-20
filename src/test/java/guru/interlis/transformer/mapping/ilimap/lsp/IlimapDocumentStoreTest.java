package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapDocumentStoreTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapDocumentStore store = new IlimapDocumentStore();

    @Test
    void openStoresDocument() {
        store.open("file:///mapping.ilimap", "text", 1);

        assertThat(store.get("file:///mapping.ilimap"))
                .hasValue(new IlimapDocumentSnapshot("file:///mapping.ilimap", "text", 1));
    }

    @Test
    void updateFullReplacesText() {
        store.open("file:///mapping.ilimap", "old", 1);

        store.updateFull("file:///mapping.ilimap", "new", 2);

        assertThat(store.get("file:///mapping.ilimap"))
                .hasValue(new IlimapDocumentSnapshot("file:///mapping.ilimap", "new", 2));
    }

    @Test
    void closeRemovesDocument() {
        store.open("file:///mapping.ilimap", "text", 1);

        store.close("file:///mapping.ilimap");

        assertThat(store.get("file:///mapping.ilimap")).isEmpty();
    }

    @Test
    void analyzeUsesStoredDocument() {
        store.open("file:///mapping.ilimap", unknownInputMapping(), 1);

        var analysis = store.analyze("file:///mapping.ilimap", OPTIONS);

        assertThat(analysis.uri()).isEqualTo("file:///mapping.ilimap");
        assertThat(analysis.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.ILIMAP_UNKNOWN_INPUT));
    }

    private static String unknownInputMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from missing class "M.A";
                  }
                }
                """;
    }
}
