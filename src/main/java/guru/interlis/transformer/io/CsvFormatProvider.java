package guru.interlis.transformer.io;

import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iom_j.csv.CsvReader;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

/**
 * Provider for delimited CSV input files via {@link CsvReader} from {@code iox-ili}.
 *
 * <p>CSV is a deliberately flat, input-only source format: a single table whose columns map to the
 * attributes of one class of the declared source model. The reader matches header columns against
 * the model attributes (or, without a header, against the attribute count). Structures, references
 * and geometry are not expressed by CSV.
 *
 * <p>Supported options:
 *
 * <ul>
 *   <li>{@code firstLineIsHeader} — {@code true}/{@code false} (default {@code true})
 *   <li>{@code separator} — single value-separator character (default {@code ,})
 *   <li>{@code delimiter} — single value-delimiter (quote) character (default {@code "})
 *   <li>{@code encoding} — character set (default {@code UTF-8} for reproducible builds)
 * </ul>
 */
public final class CsvFormatProvider implements IoxFormatProvider {

    @Override
    public String id() {
        return "csv";
    }

    @Override
    public FormatCapabilities capabilities() {
        return FormatCapabilities.readPathModelWithOptions();
    }

    @Override
    public boolean supportsInput(InputBinding binding) {
        return "csv".equalsIgnoreCase(binding.format());
    }

    @Override
    public boolean supportsOutput(OutputBinding binding) {
        return false;
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        FormatOptions options = FormatOptions.of(binding.options());

        Settings settings = new Settings();
        settings.setValue(CsvReader.ENCODING, options.getOrDefault("encoding", "UTF-8"));

        CsvReader reader = new CsvReader(binding.path().toFile(), settings);
        reader.setFirstLineIsHeader(options.getBoolean("firstLineIsHeader", true));
        reader.setValueSeparator(options.getChar("separator", ','));
        reader.setValueDelimiter(options.getChar("delimiter", '"'));
        reader.setModel(context.transferDescription());

        return new EndTransferAwareReader(reader);
    }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) {
        throw new UnsupportedOperationException("CSV output is not supported yet");
    }
}
