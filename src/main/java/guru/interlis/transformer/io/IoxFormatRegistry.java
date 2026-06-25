package guru.interlis.transformer.io;

import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Selects an {@link IoxFormatProvider} for a binding and opens the corresponding reader/writer.
 *
 * <p>The registry is the single entry point used by the runner; it keeps format selection out of the
 * generic transformation engine. Unknown formats produce a helpful exception that the caller turns
 * into a diagnostic.
 */
public final class IoxFormatRegistry {

    private final List<IoxFormatProvider> providers;

    public IoxFormatRegistry(List<IoxFormatProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /** Default registry containing the built-in INTERLIS provider plus the CSV input provider. */
    public static IoxFormatRegistry defaultRegistry() {
        return new IoxFormatRegistry(List.of(new BuiltInInterlisFormatProvider(), new CsvFormatProvider()));
    }

    /** Finds a provider that handles the given format id (e.g. {@code xtf}, {@code itf}). */
    public Optional<IoxFormatProvider> find(String formatId) {
        if (formatId == null) {
            return Optional.empty();
        }
        return providers.stream()
                .filter(p -> p.formatIds().stream().anyMatch(f -> f.equalsIgnoreCase(formatId)))
                .findFirst();
    }

    /** Finds the provider able to read the given input binding, without opening any resource. */
    public Optional<IoxFormatProvider> findForInput(InputBinding binding) {
        return providers.stream().filter(p -> p.supportsInput(binding)).findFirst();
    }

    /** Finds the provider able to write the given output binding, without opening any resource. */
    public Optional<IoxFormatProvider> findForOutput(OutputBinding binding) {
        return providers.stream().filter(p -> p.supportsOutput(binding)).findFirst();
    }

    public IoxReader createReader(InputBinding binding, FormatOpenContext context) throws Exception {
        IoxFormatProvider provider = findForInput(binding)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown input format '" + displayFormat(binding.format(), binding.path())
                                + "'. Available formats: " + availableFormats()));
        return provider.openReader(binding, context);
    }

    public IoxWriter createWriter(OutputBinding binding, FormatOpenContext context) throws Exception {
        IoxFormatProvider provider = findForOutput(binding)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown output format '" + displayFormat(binding.format(), binding.path())
                                + "'. Available formats: " + availableFormats()));
        return provider.openWriter(binding, context);
    }

    private String availableFormats() {
        return providers.stream()
                .flatMap(p -> p.formatIds().stream())
                .map(f -> f.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.joining(", "));
    }

    private static String displayFormat(String explicitFormat, Path path) {
        if (explicitFormat != null && !explicitFormat.isBlank()) {
            return explicitFormat.toLowerCase(Locale.ROOT);
        }
        return FormatIdResolver.fromPath(path).orElse("unknown");
    }
}
