package guru.interlis.transformer.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FormatOptionsTest {

    @Test
    void readsStringOption() {
        FormatOptions options = FormatOptions.of(Map.of("encoding", "UTF-8"));
        assertThat(options.get("encoding")).isEqualTo("UTF-8");
        assertThat(options.getOrDefault("encoding", "ISO-8859-1")).isEqualTo("UTF-8");
        assertThat(options.getOrDefault("missing", "fallback")).isEqualTo("fallback");
        assertThat(options.get("missing")).isNull();
    }

    @Test
    void readsBooleanOptionVariants() {
        assertThat(FormatOptions.of(Map.of("h", "true")).getBoolean("h", false)).isTrue();
        assertThat(FormatOptions.of(Map.of("h", "YES")).getBoolean("h", false)).isTrue();
        assertThat(FormatOptions.of(Map.of("h", "1")).getBoolean("h", false)).isTrue();
        assertThat(FormatOptions.of(Map.of("h", "False")).getBoolean("h", true)).isFalse();
        assertThat(FormatOptions.of(Map.of("h", "no")).getBoolean("h", true)).isFalse();
        assertThat(FormatOptions.of(Map.of("h", "0")).getBoolean("h", true)).isFalse();
        assertThat(FormatOptions.of(Map.of()).getBoolean("h", true)).isTrue();
    }

    @Test
    void rejectsInvalidBoolean() {
        FormatOptions options = FormatOptions.of(Map.of("h", "maybe"));
        assertThatThrownBy(() -> options.getBoolean("h", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("h")
                .hasMessageContaining("maybe");
    }

    @Test
    void readsCharOptionFromSingleCharacter() {
        assertThat(FormatOptions.of(Map.of("separator", ";")).getChar("separator", ','))
                .isEqualTo(';');
        assertThat(FormatOptions.of(Map.of()).getChar("separator", ',')).isEqualTo(',');
    }

    @Test
    void readsCharOptionFromNamedEscape() {
        assertThat(FormatOptions.of(Map.of("separator", "tab")).getChar("separator", ','))
                .isEqualTo('\t');
        assertThat(FormatOptions.of(Map.of("separator", "\\t")).getChar("separator", ','))
                .isEqualTo('\t');
        assertThat(FormatOptions.of(Map.of("separator", "semicolon")).getChar("separator", ','))
                .isEqualTo(';');
        assertThat(FormatOptions.of(Map.of("separator", "comma")).getChar("separator", ';'))
                .isEqualTo(',');
    }

    @Test
    void rejectsMultiCharacterCharOption() {
        FormatOptions options = FormatOptions.of(Map.of("separator", "::"));
        assertThatThrownBy(() -> options.getChar("separator", ','))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separator")
                .hasMessageContaining("::");
    }

    @Test
    void readsIntegerOption() {
        assertThat(FormatOptions.of(Map.of("fetchSize", "10000")).getInt("fetchSize", 1))
                .isEqualTo(10000);
        assertThat(FormatOptions.of(Map.of()).getInt("fetchSize", 500)).isEqualTo(500);
    }

    @Test
    void rejectsInvalidInteger() {
        FormatOptions options = FormatOptions.of(Map.of("fetchSize", "lots"));
        assertThatThrownBy(() -> options.getInt("fetchSize", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fetchSize")
                .hasMessageContaining("lots");
    }

    @Test
    void requireReturnsValueOrThrows() {
        assertThat(FormatOptions.of(Map.of("table", "municipalities")).require("table"))
                .isEqualTo("municipalities");
        assertThatThrownBy(() -> FormatOptions.of(Map.of()).require("table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("table");
    }

    @Test
    void nullValuesYieldEmptyOptions() {
        assertThat(FormatOptions.of(null).asMap()).isEmpty();
    }

    @Test
    void asMapReturnsIndependentCopy() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("a", "1");
        FormatOptions options = FormatOptions.of(source);
        Map<String, String> copy = options.asMap();
        copy.put("b", "2");
        assertThat(options.get("b")).isNull();
    }
}
