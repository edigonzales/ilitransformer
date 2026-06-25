package guru.interlis.transformer.io;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IoxFormatRegistryTest {

    private final IoxFormatRegistry registry = IoxFormatRegistry.defaultRegistry();

    @Test
    void defaultRegistryContainsInterlisProvider() {
        assertThat(registry.find("xtf")).get().isInstanceOf(BuiltInInterlisFormatProvider.class);
        assertThat(registry.find("itf")).isPresent();
        assertThat(registry.find("xml")).isPresent();
    }

    @Test
    void resolvesXtfInputByExplicitFormat() {
        assertThat(registry.find("XTF")).get().isInstanceOf(BuiltInInterlisFormatProvider.class);
        assertThat(registry.find("csv")).get().isInstanceOf(CsvFormatProvider.class);
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void resolvesXtfInputByPathExtension() {
        InputBinding binding = new InputBinding(null, Path.of("municipalities.xtf"), null, null, null, null, null);
        assertThat(registry.findForInput(binding)).get().isInstanceOf(BuiltInInterlisFormatProvider.class);
    }

    @Test
    void rejectsUnknownInputFormatWithHelpfulMessage() {
        InputBinding binding = new InputBinding(null, Path.of("source.csv"), null, null, null, null, null);
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        assertThatThrownBy(() -> registry.createReader(binding, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown input format")
                .hasMessageContaining("csv")
                .hasMessageContaining("itf, xtf, xml");
    }

    @Test
    void rejectsUnknownOutputFormatWithHelpfulMessage() {
        OutputBinding binding = new OutputBinding(null, Path.of("out.dat"), null, null, null, null, null);
        FormatOpenContext context = new FormatOpenContext(null, null, new DiagnosticCollector());
        assertThatThrownBy(() -> registry.createWriter(binding, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown output format")
                .hasMessageContaining("itf, xtf, xml");
    }
}
