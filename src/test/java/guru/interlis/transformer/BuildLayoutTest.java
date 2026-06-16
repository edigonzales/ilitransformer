package guru.interlis.transformer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class BuildLayoutTest {

    @Test
    void mainSourceDirectoryExists() {
        assertThat(Path.of("src/main/java/guru/interlis/transformer"))
                .exists()
                .isDirectory();
    }

    @Test
    void testSourceDirectoryExists() {
        assertThat(Path.of("src/test/java"))
                .exists()
                .isDirectory();
    }

    @Test
    void integrationTestSourceDirectoryExists() {
        assertThat(Path.of("src/integrationTest/java"))
                .exists()
                .isDirectory();
    }

    @Test
    void gradleWrapperExists() {
        assertThat(Path.of("gradlew")).exists().isRegularFile();
        assertThat(Path.of("gradle/wrapper/gradle-wrapper.jar")).exists();
        assertThat(Path.of("gradle/wrapper/gradle-wrapper.properties")).exists();
    }

    @Test
    void buildGradleContainsJavaToolchain() throws Exception {
        String content = Files.readString(Path.of("build.gradle"));
        assertThat(content).contains("JavaLanguageVersion.of(21)");
    }

    @Test
    void buildGradleContainsApplicationPlugin() throws Exception {
        String content = Files.readString(Path.of("build.gradle"));
        assertThat(content).contains("application");
    }

    @Test
    void buildGradleDefinesIntegrationTestSourceSet() throws Exception {
        String content = Files.readString(Path.of("build.gradle"));
        assertThat(content).contains("integrationTest");
    }

    @Test
    void buildGradleCheckDependsOnIntegrationTest() throws Exception {
        String content = Files.readString(Path.of("build.gradle"));
        assertThat(content).contains("check.dependsOn");
    }

    @Test
    void integrationTestDirectoryIsNotEmpty() throws Exception {
        Path dir = Path.of("src/integrationTest/java/guru/interlis/transformer");
        assertThat(dir).exists().isDirectory();
        var files = Files.list(dir).filter(p -> p.toString().endsWith(".java")).toList();
        assertThat(files).as("integrationTest directory must contain test classes").isNotEmpty();
    }

    @Test
    void settingsGradleExists() {
        assertThat(Path.of("settings.gradle")).exists().isRegularFile();
    }
}
