package guru.interlis.transformer.io;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvFormatProviderTest {

    private final CsvFormatProvider provider = new CsvFormatProvider();

    @Test
    void exposesCsvIdentityAndReadOnlyCapabilities() {
        assertThat(provider.id()).isEqualTo("csv");
        assertThat(provider.formatIds()).containsExactly("csv");

        FormatCapabilities caps = provider.capabilities();
        assertThat(caps.canRead()).isTrue();
        assertThat(caps.canWrite()).isFalse();
        assertThat(caps.requiresPath()).isTrue();
        assertThat(caps.requiresModel()).isTrue();
        assertThat(caps.supportsOptions()).isTrue();
    }

    @Test
    void supportsCsvInputByFormatId() {
        assertThat(provider.supportsInput(inputWithFormat("csv"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("CSV"))).isTrue();
        assertThat(provider.supportsInput(inputWithFormat("xtf"))).isFalse();
        assertThat(provider.supportsInput(inputWithFormat(null))).isFalse();
    }

    @Test
    void doesNotSupportOutput() {
        assertThat(provider.supportsOutput(new OutputBinding(null, Path.of("a.csv"), null, "csv", null, null, null)))
                .isFalse();
    }

    @Test
    void openWriterIsUnsupported() {
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        OutputBinding binding = new OutputBinding(null, Path.of("a.csv"), null, "csv", null, null, null);
        assertThatThrownBy(() -> provider.openWriter(binding, context))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("CSV output is not supported");
    }

    @Test
    void rejectsInvalidSeparatorOptionWithHelpfulMessage(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("data.csv");
        Files.writeString(csv, "a;b\n1;2\n");

        InputBinding binding =
                new InputBinding("in", csv, "DemoCsvSource", "csv", Map.of("separator", "::"), null, null);
        FormatOpenContext context = new FormatOpenContext(tempDir, null, new DiagnosticCollector());

        assertThatThrownBy(() -> provider.openReader(binding, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separator")
                .hasMessageContaining("::");
    }

    private static InputBinding inputWithFormat(String format) {
        return new InputBinding(null, Path.of("a.csv"), null, format, null, null, null);
    }
}
