package guru.interlis.transformer.interlis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InterlisModelLoaderTest {

    @Test
    void normalizesRemoteModelDirectoriesWithoutBreakingScheme() {
        assertThat(InterlisModelLoader.normalizeModelDirectoryString(
                        " https://models.geo.admin.ch/ ; ; http://example.test// "))
                .isEqualTo("https://models.geo.admin.ch;http://example.test");
    }

    @Test
    void keepsLocalModelDirectoriesAsTrimmedPaths() {
        assertThat(InterlisModelLoader.normalizeModelDirectoryString(" src/test/data/models/ ; /tmp/models// "))
                .isEqualTo("src/test/data/models/;/tmp/models//");
    }
}
