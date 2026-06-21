package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions;
import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatter;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

class IlimapFormattingLspTest {

    private static final String URI = "file:///mapping.ilimap";

    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void serverAdvertisesDocumentFormattingCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getDocumentFormattingProvider().getLeft())
                .isTrue();
    }

    @Test
    void formattingReturnsSingleFullDocumentEdit() {
        String source = validCompactMapping();
        open(source);

        List<? extends TextEdit> edits = format();

        assertThat(edits).singleElement().satisfies(edit -> {
            assertThat(edit.getRange()).isEqualTo(new Range(new Position(0, 0), new Position(0, source.length())));
            assertThat(edit.getNewText()).endsWith("\n");
        });
    }

    @Test
    void formattingUsesIlimapFormatterOutput() {
        String source = validCompactMappingWithAssignment();
        String expected =
                new IlimapFormatter().format(new IlimapParser(source).parseDocument(), IlimapFormatOptions.defaults());
        open(source);

        List<? extends TextEdit> edits = format();

        assertThat(edits).singleElement().satisfies(edit -> assertThat(edit.getNewText())
                .isEqualTo(expected));
    }

    @Test
    void formattingReturnsNoEditForInvalidDocument() {
        open(missingSemicolonMapping());

        assertThat(format()).isEmpty();
    }

    private void open(String source) {
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
    }

    private List<? extends TextEdit> format() {
        return service.formatting(
                        new DocumentFormattingParams(new TextDocumentIdentifier(URI), new FormattingOptions(2, true)))
                .join();
    }

    private static String validCompactMapping() {
        return "mapping v2 { input src { path \"in.xtf\"; model \"M\"; } output out { path \"out.xtf\"; model \"M\"; } rule r1 { target out class \"M.A\"; source s from src class \"M.A\"; } }";
    }

    private static String validCompactMappingWithAssignment() {
        return "mapping v2 { input src { path \"in.xtf\"; model \"M\"; } output out { path \"out.xtf\"; model \"M\"; } rule r1 { target out class \"M.A\"; source s from src class \"M.A\"; assign { X = s.X; } } }";
    }

    private static String missingSemicolonMapping() {
        return """
                mapping v2 {
                  input src {
                    path "in.xtf"
                    model "M";
                  }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }
}
