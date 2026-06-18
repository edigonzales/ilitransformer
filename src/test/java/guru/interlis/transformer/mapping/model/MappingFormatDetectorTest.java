package guru.interlis.transformer.mapping.model;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MappingFormatDetectorTest {

    private final MappingFormatDetector detector = new MappingFormatDetector();

    @Test
    void detectsIlimapExtension() {
        assertThat(detector.detect(Path.of("profile.ilimap"))).isEqualTo(MappingFormat.ILIMAP);
        assertThat(detector.detect(Path.of("path/to/mapping.ilimap"))).isEqualTo(MappingFormat.ILIMAP);
    }

    @Test
    void detectsYamlExtension() {
        assertThat(detector.detect(Path.of("profile.yaml"))).isEqualTo(MappingFormat.YAML);
        assertThat(detector.detect(Path.of("path/to/mapping.yaml"))).isEqualTo(MappingFormat.YAML);
    }

    @Test
    void detectsYmlExtension() {
        assertThat(detector.detect(Path.of("profile.yml"))).isEqualTo(MappingFormat.YAML);
    }

    @Test
    void rejectsUnknownExtension() {
        assertThatThrownBy(() -> detector.detect(Path.of("profile.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported mapping file extension");
    }

    @Test
    void caseInsensitive() {
        assertThat(detector.detect(Path.of("profile.ILIMAP"))).isEqualTo(MappingFormat.ILIMAP);
        assertThat(detector.detect(Path.of("profile.YAML"))).isEqualTo(MappingFormat.YAML);
    }
}
