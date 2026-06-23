package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class BuildInfoTest {

    @Test
    void senderIdFromGeneratedResourceHasExpectedShape() {
        BuildInfo info = BuildInfo.get();

        assertThat(info.name()).isEqualTo("ilitransformer");
        assertThat(info.version()).isNotBlank();
        assertThat(info.gitCommit()).isNotBlank();
        assertThat(info.senderId()).matches("ilitransformer-.+-([0-9a-f]{40}|unknown)");
        assertThat(info.versionLine())
                .contains(info.name())
                .contains(info.version())
                .contains(info.gitCommit());
    }

    @Test
    void fromPropertiesUsesAllValues() {
        Properties props = new Properties();
        props.setProperty("name", "ilitransformer");
        props.setProperty("version", "1.2.3");
        props.setProperty("gitCommit", "abc123def4567890abc123def4567890abc12345");

        BuildInfo info = BuildInfo.fromProperties(props);

        assertThat(info.senderId()).isEqualTo("ilitransformer-1.2.3-abc123def4567890abc123def4567890abc12345");
        assertThat(info.versionLine()).isEqualTo("ilitransformer 1.2.3 (abc123def4567890abc123def4567890abc12345)");
    }

    @Test
    void fromEmptyPropertiesFallsBackToDefaults() {
        BuildInfo info = BuildInfo.fromProperties(new Properties());

        assertThat(info.name()).isEqualTo(BuildInfo.DEFAULT_NAME);
        assertThat(info.gitCommit()).isEqualTo(BuildInfo.UNKNOWN_COMMIT);
        assertThat(info.version()).isNotBlank();
        assertThat(info.senderId()).matches("ilitransformer-.+-unknown");
    }
}
