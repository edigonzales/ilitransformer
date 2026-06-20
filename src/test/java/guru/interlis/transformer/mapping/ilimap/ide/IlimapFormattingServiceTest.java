package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions;
import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatter;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import org.junit.jupiter.api.Test;

class IlimapFormattingServiceTest {

    private final IlimapFormattingService service = new IlimapFormattingService();

    @Test
    void formatsValidDocumentWithFullDocumentEdit() {
        String source =
                "mapping v2 { input src { path \"in.xtf\"; model \"M\"; } output out { path \"out.xtf\"; model \"M\"; } rule r1 { target out class \"M.A\"; source s from src class \"M.A\"; } }";

        var edit = service.format("file:///test.ilimap", source, IlimapFormatOptions.defaults());

        assertThat(edit).isPresent();
        assertThat(edit.orElseThrow().range())
                .isEqualTo(new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapLineMap(source).toIdePosition(source.length())));
        assertThat(edit.orElseThrow().newText()).contains("input src {");
        assertThat(edit.orElseThrow().newText()).endsWith("\n");
    }

    @Test
    void doesNotFormatInvalidDocument() {
        String source = """
                mapping v2 {
                  input src {
                    path "in.xtf"
                  }
                }
                """;

        assertThat(service.format("file:///test.ilimap", source, IlimapFormatOptions.defaults())).isEmpty();
    }

    @Test
    void preservesFormatterOutputFromIlimapFormatter() {
        String source =
                "mapping v2 { input src { path \"in.xtf\"; model \"M\"; } output out { path \"out.xtf\"; model \"M\"; } rule r1 { target out class \"M.A\"; source s from src class \"M.A\"; assign { X = s.X; } } }";
        var options = new IlimapFormatOptions(4, false, true);
        var document = new IlimapParser(source).parseDocument();
        String expected = new IlimapFormatter().format(document, options);

        var edit = service.format("file:///test.ilimap", source, options);

        assertThat(edit).isPresent();
        assertThat(edit.orElseThrow().newText()).isEqualTo(expected);
    }
}
