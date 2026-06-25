package guru.interlis.transformer.io;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BuiltInInterlisFormatProviderTest {

    private final BuiltInInterlisFormatProvider provider = new BuiltInInterlisFormatProvider();

    @Test
    void exposesInterlisIdentityAndCapabilities() {
        assertThat(provider.id()).isEqualTo("interlis");
        assertThat(provider.formatIds()).containsExactlyInAnyOrder("itf", "xtf", "xml");
        FormatCapabilities caps = provider.capabilities();
        assertThat(caps.canRead()).isTrue();
        assertThat(caps.canWrite()).isTrue();
    }

    @Test
    void supportsInterlisInputExtensions() {
        assertThat(provider.supportsInput(inputWithPath("a.itf"))).isTrue();
        assertThat(provider.supportsInput(inputWithPath("a.xtf"))).isTrue();
        assertThat(provider.supportsInput(inputWithPath("a.xml"))).isTrue();
        assertThat(provider.supportsInput(inputWithPath("a.csv"))).isFalse();
        assertThat(provider.supportsInput(new InputBinding(null, null, null, null, null, null, null)))
                .isFalse();
    }

    @Test
    void supportsInterlisOutputExtensions() {
        assertThat(provider.supportsOutput(outputWithPath("a.itf"))).isTrue();
        assertThat(provider.supportsOutput(outputWithPath("a.xtf"))).isTrue();
        assertThat(provider.supportsOutput(outputWithPath("a.xml"))).isTrue();
        assertThat(provider.supportsOutput(outputWithPath("a.dat"))).isFalse();
    }

    @Test
    void openReaderRejectsUnsupportedExtension() {
        FormatOpenContext context = new FormatOpenContext(null, null, null);
        assertThatThrownBy(() -> provider.openReader(inputWithPath("source.csv"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported input file type");
    }

    @Test
    void openWriterRejectsUnsupportedExtension() {
        FormatOpenContext context = new FormatOpenContext(null, null, null);
        assertThatThrownBy(() -> provider.openWriter(outputWithPath("out.dat"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported output file type");
    }

    private static InputBinding inputWithPath(String name) {
        return new InputBinding(null, Path.of(name), null, null, null, null, null);
    }

    private static OutputBinding outputWithPath(String name) {
        return new OutputBinding(null, Path.of(name), null, null, null, null, null);
    }
}
