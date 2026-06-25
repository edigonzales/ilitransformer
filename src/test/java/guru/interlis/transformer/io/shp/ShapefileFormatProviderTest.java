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
    void exposesShapefileIdentityAndReadWriteCapabilities() {
        assertThat(provider.id()).isEqualTo("shp");
        assertThat(provider.formatIds()).containsExactlyInAnyOrder("shp", "shapefile");

        FormatCapabilities caps = provider.capabilities();
        assertThat(caps.canRead()).isTrue();
        assertThat(caps.canWrite()).isTrue();
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
    void supportsShpAndShapefileOutputByFormatId() {
        assertThat(provider.supportsOutput(outputWithFormat("shp"))).isTrue();
        assertThat(provider.supportsOutput(outputWithFormat("SHAPEFILE"))).isTrue();
        assertThat(provider.supportsOutput(outputWithFormat("xtf"))).isFalse();
        assertThat(provider.supportsOutput(outputWithFormat(null))).isFalse();
    }

    @Test
    void openWriterReturnsShapefileWriter() throws Exception {
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        OutputBinding binding = new OutputBinding(
                "out", Path.of("out.shp"), null, "shp", java.util.Map.of("class", "Demo.Topic.Cls"), null, null);

        assertThat(provider.openWriter(binding, context)).isNotNull();
    }

    @Test
    void openReaderFailsWithHelpfulMessageForMissingFile() {
        InputBinding binding =
                new InputBinding("parcels", Path.of("parcels.shp"), "DemoShpSource", "shp", null, null, null);
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        assertThatThrownBy(() -> provider.openReader(binding, context))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("parcels");
    }

    private static InputBinding inputWithFormat(String format) {
        return new InputBinding(null, Path.of("a.shp"), null, format, null, null, null);
    }

    private static OutputBinding outputWithFormat(String format) {
        return new OutputBinding(null, Path.of("a.shp"), null, format, null, null, null);
    }
}
