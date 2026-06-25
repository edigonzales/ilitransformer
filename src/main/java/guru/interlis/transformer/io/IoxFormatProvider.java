package guru.interlis.transformer.io;

import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

import java.util.Set;

/**
 * Abstraction over a transfer/data format that can be read and/or written as an IOX event stream.
 *
 * <p>Providers translate a format-neutral {@link InputBinding}/{@link OutputBinding} into a concrete
 * {@link IoxReader}/{@link IoxWriter}. The generic transformation engine never depends on a concrete
 * format; it only ever sees {@code IoxReader}/{@code IoxWriter}.
 */
public interface IoxFormatProvider {

    /** Primary, lower-case identifier of this provider (e.g. {@code interlis}, {@code csv}). */
    String id();

    /** Declares what this provider can do (read/write, path/model/option requirements). */
    FormatCapabilities capabilities();

    /**
     * Concrete format ids this provider handles. Defaults to {@link #id()}; providers that cover
     * several formats (such as the built-in INTERLIS provider for {@code itf}/{@code xtf}/{@code
     * xml}) override this.
     */
    default Set<String> formatIds() {
        return Set.of(id());
    }

    /** Whether this provider can open a reader for the given input binding. */
    boolean supportsInput(InputBinding binding);

    /** Whether this provider can open a writer for the given output binding. */
    boolean supportsOutput(OutputBinding binding);

    /** Opens a reader for the given input. Exceptions are turned into diagnostics by the caller. */
    IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception;

    /** Opens a writer for the given output. Exceptions are turned into diagnostics by the caller. */
    IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) throws Exception;
}
