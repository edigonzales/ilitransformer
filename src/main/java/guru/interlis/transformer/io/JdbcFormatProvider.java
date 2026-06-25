package guru.interlis.transformer.io;

import guru.interlis.transformer.io.jdbc.JdbcIoxReader;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

/**
 * Provider for generic, tabular JDBC input. Each configured query maps to one flat source class and
 * becomes one basket in the produced IOX event stream.
 *
 * <p>JDBC is read-only and has no file path; the connection and queries are declared on the input
 * binding ({@code connection} / {@code queries}). The declared source model describes the flat query
 * result classes. Geometry is not handled in this phase.
 *
 * <p>Supported options:
 *
 * <ul>
 *   <li>{@code blobEncoding} — {@code base64} to encode {@code byte[]} columns; otherwise binary
 *       columns are rejected.
 * </ul>
 */
public final class JdbcFormatProvider implements IoxFormatProvider {

    @Override
    public String id() {
        return "jdbc";
    }

    @Override
    public FormatCapabilities capabilities() {
        return FormatCapabilities.readConnectionModelWithOptions();
    }

    @Override
    public boolean supportsInput(InputBinding binding) {
        return "jdbc".equalsIgnoreCase(binding.format());
    }

    @Override
    public boolean supportsOutput(OutputBinding binding) {
        return false;
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) {
        return JdbcIoxReader.open(binding, context);
    }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) {
        throw new UnsupportedOperationException("JDBC output is not supported");
    }
}
