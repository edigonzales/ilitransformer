package guru.interlis.transformer.io.shp;

import guru.interlis.transformer.io.FormatCapabilities;
import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.io.IoxFormatProvider;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

import java.util.Set;

/**
 * Provider for ESRI Shapefile input via the format ids {@code shp} and {@code shapefile}.
 *
 * <p>This is the phase&nbsp;1 skeleton: the provider is a <em>known</em> input provider so that
 * Shapefile bindings are routed here instead of failing as an unknown format, but the actual reader
 * is not implemented yet and reports a controlled {@link ShapefileMappingException}. Later phases add
 * the GeoTools-free Shapefile core, the geometry decoder and the IOX reader/writer adapters.
 *
 * <p>This implementation deliberately carries no GeoTools dependency and no binary Shapefile parsing
 * logic. The generic transformation engine never depends on this class; it only ever sees
 * {@link IoxReader}/{@link IoxWriter}.
 */
public final class ShapefileFormatProvider implements IoxFormatProvider {

    private static final Set<String> FORMAT_IDS = Set.of("shp", "shapefile");

    @Override
    public String id() {
        return "shp";
    }

    @Override
    public Set<String> formatIds() {
        return FORMAT_IDS;
    }

    @Override
    public FormatCapabilities capabilities() {
        return FormatCapabilities.readPathModelWithOptions();
    }

    @Override
    public boolean supportsInput(InputBinding binding) {
        if (binding.format() == null) {
            return false;
        }
        return FORMAT_IDS.stream().anyMatch(f -> f.equalsIgnoreCase(binding.format()));
    }

    @Override
    public boolean supportsOutput(OutputBinding binding) {
        return false;
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws ShapefileMappingException {
        String inputId = binding.inputId() != null ? binding.inputId() : "?";
        throw new ShapefileMappingException("SHP input '" + inputId
                + "': Shapefile reader is not implemented yet. Format '"
                + (binding.format() != null ? binding.format() : "shp")
                + "' is a known provider but reading is added in a later phase.");
    }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) {
        throw new UnsupportedOperationException("Shapefile output is not supported yet");
    }
}
