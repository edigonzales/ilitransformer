package guru.interlis.transformer.interlis;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.io.BuiltInInterlisFormatProvider;
import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

import java.nio.file.Path;

/**
 * Backward-compatible adapter over the format-provider architecture.
 *
 * <p>This class no longer owns any format dispatch logic; it forwards path-based
 * reader/writer creation to {@link BuiltInInterlisFormatProvider}. Existing callers that only have a
 * {@link Path} and a {@link TransferDescription} keep working unchanged.
 */
public final class InterlisIoFactory {

    private final BuiltInInterlisFormatProvider provider = new BuiltInInterlisFormatProvider();

    public IoxReader createReader(Path path, TransferDescription transferDescription) throws Exception {
        InputBinding binding = new InputBinding(null, path, null, null, transferDescription, null);
        FormatOpenContext context = new FormatOpenContext(null, transferDescription, null);
        return provider.openReader(binding, context);
    }

    public IoxWriter createWriter(Path path, TransferDescription transferDescription) throws Exception {
        return createWriter(path, transferDescription, null);
    }

    public IoxWriter createWriter(Path path, TransferDescription transferDescription, DiagnosticCollector diagnostics)
            throws Exception {
        OutputBinding binding = new OutputBinding(null, path, null, null, transferDescription, null);
        FormatOpenContext context = new FormatOpenContext(null, transferDescription, diagnostics);
        return provider.openWriter(binding, context);
    }
}
