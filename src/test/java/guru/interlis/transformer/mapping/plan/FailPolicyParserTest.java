package guru.interlis.transformer.mapping.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FailPolicyParserTest {

    @Test
    void parseOrDefaultReturnsStrict() {
        assertThat(FailPolicyParser.parseOrDefault("strict", FailPolicy.STRICT)).isEqualTo(FailPolicy.STRICT);
        assertThat(FailPolicyParser.parseOrDefault("STRICT", FailPolicy.STRICT)).isEqualTo(FailPolicy.STRICT);
    }

    @Test
    void parseOrDefaultReturnsLenient() {
        assertThat(FailPolicyParser.parseOrDefault("lenient", FailPolicy.STRICT))
                .isEqualTo(FailPolicy.LENIENT);
        assertThat(FailPolicyParser.parseOrDefault("LENIENT", FailPolicy.STRICT))
                .isEqualTo(FailPolicy.LENIENT);
    }

    @Test
    void parseOrDefaultReturnsReportOnly() {
        assertThat(FailPolicyParser.parseOrDefault("report_only", FailPolicy.STRICT))
                .isEqualTo(FailPolicy.REPORT_ONLY);
        assertThat(FailPolicyParser.parseOrDefault("reportOnly", FailPolicy.STRICT))
                .isEqualTo(FailPolicy.REPORT_ONLY);
    }

    @Test
    void parseOrDefaultReturnsDefaultForNull() {
        assertThat(FailPolicyParser.parseOrDefault(null, FailPolicy.LENIENT)).isEqualTo(FailPolicy.LENIENT);
        assertThat(FailPolicyParser.parseOrDefault(null, FailPolicy.STRICT)).isEqualTo(FailPolicy.STRICT);
    }

    @Test
    void parseOrDefaultReturnsDefaultForBlank() {
        assertThat(FailPolicyParser.parseOrDefault("  ", FailPolicy.LENIENT)).isEqualTo(FailPolicy.LENIENT);
    }

    @Test
    void parseOrDefaultThrowsForInvalidValue() {
        assertThatThrownBy(() -> FailPolicyParser.parseOrDefault("invalid", FailPolicy.STRICT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid")
                .hasMessageContaining("valid values");
    }
}
