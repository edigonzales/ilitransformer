package guru.interlis.transformer.io.shp;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.io.FormatCapabilities;
import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ShapefileFormatProviderTest {

    private final ShapefileFormatProvider provider = new ShapefileFormatProvider();

    @Test
    void exposesShapefileIdentityAndReadOnlyCapabilities() {
        assertThat(provider.id()).isEqualTo("shp");
        assertThat(provider.formatIds()).containsExactlyInAnyOrder("shp", "shapefile");

        FormatCapabilities caps = provider.capabilities();
        assertThat(caps.canRead()).isTrue();
        assertThat(caps.canWrite()).isFalse();
        assertThat(caps.requiresPath()).isTrue();
        assertThat(caps.requiresModel()).isTrue();
        assertThat(caps.supportsOptions()).isTrue();
    }

    @Test
    void supportsShpAndShapefileInputByFormatId() {
        assertThat(provider.supportsInput(inputWithFormat("shp"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("SHP"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("shapefile"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("SHAPEFILE"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("xtf"))).isFalse();
        assertThat(provider.supportsInput(inputWithFormat("csv"))).isFalse();
        assertThat(provider.supportsInput(inputWithFormat(null))).isFalse();
    }

    @Test
    void doesNotSupportOutput() {
        assertThat(provider.supportsOutput(new OutputBinding(null, Path.of("a.shp"), null, "shp", null, null, null)))
                .isFalse();
    }

    @Test
    void openWriterIsUnsupported() {
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        OutputBinding binding = new OutputBinding(null, Path.of("a.shp"), null, "shp", null, null, null);
        assertThatThrownBy(() -> provider.openWriter(binding, context))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Shapefile output is not supported");
    }

    @Test
    void openReaderReportsControlledNotImplemented() {
        InputBinding binding =
                new InputBinding("parcels", Path.of("parcels.shp"), "DemoShpSource", "shp", null, null, null);
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        assertThatThrownBy(() -> provider.openReader(binding, context))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("parcels")
                .hasMessageContaining("not implemented");
    }

    private static InputBinding inputWithFormat(String format) {
        return new InputBinding(null, Path.of("a.shp"), null, format, null, null, null);
    }
}
