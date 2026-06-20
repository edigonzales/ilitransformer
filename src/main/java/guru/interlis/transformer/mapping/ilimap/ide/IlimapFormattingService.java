package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions;
import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatter;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapExpressionReader;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import java.util.Objects;
import java.util.Optional;

public final class IlimapFormattingService {

    private final IlimapFormatter formatter = new IlimapFormatter();

    public Optional<IlimapTextEdit> format(String uri, String text, IlimapFormatOptions options) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(text, "text");

        IlimapDocument document;
        try {
            document = new IlimapParser(text).parseDocument();
        } catch (IlimapParser.ParseException | IlimapExpressionReader.ReaderException e) {
            return Optional.empty();
        }

        IlimapFormatOptions effectiveOptions = options != null ? options : IlimapFormatOptions.defaults();
        String formatted = formatter.format(document, effectiveOptions);
        IlimapLineMap lineMap = new IlimapLineMap(text);
        IlimapIdeRange fullDocumentRange =
                new IlimapIdeRange(new IlimapIdePosition(0, 0), lineMap.toIdePosition(text.length()));
        return Optional.of(new IlimapTextEdit(fullDocumentRange, formatted));
    }
}
