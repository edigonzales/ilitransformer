package guru.interlis.transformer.io.shp;

import guru.interlis.transformer.io.EndTransferAwareReader;
import guru.interlis.transformer.io.FormatCapabilities;
import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.io.IoxFormatProvider;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

import java.util.Set;

/**
 * Provider for ESRI Shapefile input and output via the format ids {@code shp} and {@code shapefile}.
 *
 * <p>The reader maps one flat Shapefile dataset to a single source class; the writer serialises a
 * single IOX class with one geometry type into one Shapefile dataset. Both sides are GeoTools-free
 * and carry no binary parsing logic in the engine: the generic transformation engine only ever sees
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
        return FormatCapabilities.readWritePathModelWithOptions();
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
        if (binding.format() == null) {
            return false;
        }
        return FORMAT_IDS.stream().anyMatch(f -> f.equalsIgnoreCase(binding.format()));
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        return new EndTransferAwareReader(ShapefileIoxReader.open(binding, context));
    }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) throws Exception {
        return ShapefileIoxWriter.open(binding, context);
    }
}
