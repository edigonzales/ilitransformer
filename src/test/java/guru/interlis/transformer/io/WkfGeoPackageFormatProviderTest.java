package guru.interlis.transformer.io;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WkfGeoPackageFormatProviderTest {

    private final WkfGeoPackageFormatProvider provider = new WkfGeoPackageFormatProvider();

    @Test
    void exposesGpkgIdentityAndReadOnlyCapabilities() {
        assertThat(provider.id()).isEqualTo("gpkg");
        assertThat(provider.formatIds()).containsExactlyInAnyOrder("gpkg", "geopackage");

        FormatCapabilities caps = provider.capabilities();
        assertThat(caps.canRead()).isTrue();
        assertThat(caps.canWrite()).isFalse();
        assertThat(caps.requiresPath()).isTrue();
        assertThat(caps.requiresModel()).isTrue();
        assertThat(caps.supportsOptions()).isTrue();
    }

    @Test
    void supportsGpkgAndGeopackageInputByFormatId() {
        assertThat(provider.supportsInput(inputWithFormat("gpkg"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("GPKG"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("geopackage"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("GEOPACKAGE"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("xtf"))).isFalse();
        assertThat(provider.supportsInput(inputWithFormat("csv"))).isFalse();
        assertThat(provider.supportsInput(inputWithFormat(null))).isFalse();
    }

    @Test
    void doesNotSupportOutput() {
        assertThat(provider.supportsOutput(new OutputBinding(null, Path.of("a.gpkg"), null, "gpkg", null, null, null)))
                .isFalse();
    }

    @Test
    void openWriterIsUnsupported() {
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        OutputBinding binding = new OutputBinding(null, Path.of("a.gpkg"), null, "gpkg", null, null, null);
        assertThatThrownBy(() -> provider.openWriter(binding, context))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("GeoPackage output is not supported");
    }

    @Test
    void requiresTableOption() {
        InputBinding binding =
                new InputBinding("in", Path.of("data.gpkg"), "DemoGpkgSource", "gpkg", Map.of(), null, null);
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());

        assertThatThrownBy(() -> provider.openReader(binding, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("table");
    }

    private static InputBinding inputWithFormat(String format) {
        return new InputBinding(null, Path.of("a.gpkg"), null, format, null, null, null);
    }
}
