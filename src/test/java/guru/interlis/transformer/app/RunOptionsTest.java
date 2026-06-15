package guru.interlis.transformer.app;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.plan.FailPolicy;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunOptionsTest {

    @Test
    void defaultConstructorSetsAllToDefaults() {
        RunOptions options = new RunOptions();
        assertThat(options.modelDirectories()).isEmpty();
        assertThat(options.validateOutput()).isFalse();
        assertThat(options.reportDirectory()).isNull();
        assertThat(options.keepTemporaryFiles()).isFalse();
        assertThat(options.failPolicyOverrideOptional()).isEmpty();
    }

    @Test
    void modelDirectoriesConstructorSetsDirectoriesAndDefaults() {
        RunOptions options = new RunOptions(List.of("dir1", "dir2"));
        assertThat(options.modelDirectories()).containsExactly("dir1", "dir2");
        assertThat(options.validateOutput()).isFalse();
        assertThat(options.failPolicyOverrideOptional()).isEmpty();
    }

    @Test
    void fourArgConstructorWorks() {
        Path report = Path.of("/tmp/report");
        RunOptions options = new RunOptions(List.of(), true, report, true);
        assertThat(options.validateOutput()).isTrue();
        assertThat(options.reportDirectory()).isEqualTo(report);
        assertThat(options.keepTemporaryFiles()).isTrue();
        assertThat(options.failPolicyOverrideOptional()).isEmpty();
    }

    @Test
    void failPolicyOverridePresentWhenSet() {
        RunOptions options = new RunOptions(List.of(), false, null, false, FailPolicy.LENIENT);
        assertThat(options.failPolicyOverrideOptional()).hasValue(FailPolicy.LENIENT);
    }

    @Test
    void failPolicyOverrideEmptyWhenNull() {
        RunOptions options = new RunOptions(List.of(), false, null, false, null);
        assertThat(options.failPolicyOverrideOptional()).isEmpty();
    }

    @Test
    void failPolicyOverrideEmptyWithFourArgConstructor() {
        RunOptions options = new RunOptions(List.of(), false, null, false);
        assertThat(options.failPolicyOverrideOptional()).isEmpty();
    }
}
