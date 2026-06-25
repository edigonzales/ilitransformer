package guru.interlis.transformer.io;

import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import ch.interlis.ioxwkf.gpkg.GeoPackageReader;

import java.util.Set;

/**
 * Provider for tabular GeoPackage input files via {@link GeoPackageReader} from {@code iox-wkf}.
 *
 * <p>GeoPackage is a read-only source format in this phase. It reads a single table from a
 * GeoPackage file and maps its columns to the attributes of one class of the declared source model.
 * Geometry is not required; a purely tabular GeoPackage with {@code data_type=attributes} works.
 *
 * <p>Supported options:
 *
 * <ul>
 *   <li>{@code table} — database table name (required)
 *   <li>{@code fetchSize} — rows to fetch at once (default {@code 10000})
 * </ul>
 *
 * <p>Both format ids {@code gpkg} and {@code geopackage} are accepted (case-insensitive).
 */
public final class WkfGeoPackageFormatProvider implements IoxFormatProvider {

    private static final Set<String> FORMAT_IDS = Set.of("gpkg", "geopackage");

    @Override
    public String id() {
        return "gpkg";
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
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        FormatOptions options = FormatOptions.of(binding.options());
        String table = options.require("table");
        int fetchSize = options.getInt("fetchSize", 10000);

        Settings settings = new Settings();
        settings.setValue(IoxWkfConfig.SETTING_GPKGTABLE, table);
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, table);
        settings.setValue(IoxWkfConfig.SETTING_FETCHSIZE, Integer.toString(fetchSize));

        GeoPackageReader reader = new GeoPackageReader(binding.path().toFile(), table, settings);
        reader.setModel(context.transferDescription());
        return new EndTransferAwareReader(reader);
    }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) {
        throw new UnsupportedOperationException("GeoPackage output is not supported yet");
    }
}
