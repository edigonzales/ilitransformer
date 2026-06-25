package guru.interlis.transformer.io;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FormatIdResolverTest {

    @Test
    void explicitInputFormatWinsOverExtension() {
        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.format = "XTF";
        input.path = "data.itf";
        assertThat(FormatIdResolver.resolveInputFormat(input)).isEqualTo("xtf");
    }

    @Test
    void inputFormatFallsBackToPathExtension() {
        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.path = "data.itf";
        assertThat(FormatIdResolver.resolveInputFormat(input)).isEqualTo("itf");
    }

    @Test
    void inputFormatWithoutPathRequiresExplicitFormat() {
        JobConfig.InputSpec jdbc = new JobConfig.InputSpec();
        jdbc.format = "jdbc";
        assertThat(FormatIdResolver.resolveInputFormat(jdbc)).isEqualTo("jdbc");

        JobConfig.InputSpec none = new JobConfig.InputSpec();
        assertThat(FormatIdResolver.resolveInputFormat(none)).isNull();
    }

    @Test
    void explicitOutputFormatWinsOverExtension() {
        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.format = "ITF";
        output.path = "out.xtf";
        assertThat(FormatIdResolver.resolveOutputFormat(output)).isEqualTo("itf");
    }

    @Test
    void outputFormatFallsBackToPathExtension() {
        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.path = "out.xtf";
        assertThat(FormatIdResolver.resolveOutputFormat(output)).isEqualTo("xtf");
    }

    @Test
    void fromPathRecognizesKnownExtensions() {
        assertThat(FormatIdResolver.fromPath(Path.of("a.xtf"))).contains("xtf");
        assertThat(FormatIdResolver.fromPath(Path.of("a.xml"))).contains("xml");
        assertThat(FormatIdResolver.fromPath(Path.of("a.itf"))).contains("itf");
        assertThat(FormatIdResolver.fromPath(Path.of("a.csv"))).contains("csv");
        assertThat(FormatIdResolver.fromPath(Path.of("a.gpkg"))).contains("gpkg");
        assertThat(FormatIdResolver.fromPath(Path.of("a.shp"))).contains("shp");
    }

    @Test
    void fromPathReturnsEmptyForUnknownOrNull() {
        assertThat(FormatIdResolver.fromPath(Path.of("a.dat"))).isEmpty();
        assertThat(FormatIdResolver.fromPath(null)).isEmpty();
    }
}
