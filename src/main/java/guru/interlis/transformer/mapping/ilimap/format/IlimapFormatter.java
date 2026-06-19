package guru.interlis.transformer.mapping.ilimap.format;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;

// Comments are not preserved during formatting because the AST does not store them.
public final class IlimapFormatter {

    public String format(IlimapDocument document) {
        return format(document, IlimapFormatOptions.defaults());
    }

    public String format(IlimapDocument document, IlimapFormatOptions options) {
        return new IlimapPrinter(options).print(document);
    }
}
