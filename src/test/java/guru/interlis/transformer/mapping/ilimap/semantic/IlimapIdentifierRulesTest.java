package guru.interlis.transformer.mapping.ilimap.semantic;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import org.junit.jupiter.api.Test;

class IlimapIdentifierRulesTest {

    private static final IlimapSourceRange DUMMY_RANGE =
            new IlimapSourceRange(new IlimapSourcePosition(0, 1, 1), new IlimapSourcePosition(1, 1, 2));

    @Test
    void acceptsHyphenInRuleId() {
        assertThat(IlimapIdentifierRules.isValidSymbolId("my-rule")).isTrue();
        assertThat(IlimapIdentifierRules.isValidSymbolId("dm01-to-dmav")).isTrue();

        var diagnostics = new DiagnosticCollector();
        IlimapIdentifierRules.requireSymbolId("my-rule", diagnostics, DUMMY_RANGE);
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    void rejectsHyphenInSourceAlias() {
        assertThat(IlimapIdentifierRules.isValidAliasId("my-alias")).isFalse();

        var diagnostics = new DiagnosticCollector();
        IlimapIdentifierRules.requireAliasId("my-alias", diagnostics, DUMMY_RANGE);
        assertThat(diagnostics.all()).hasSize(1);
        assertThat(diagnostics.all().get(0).code()).isEqualTo(DiagnosticCode.ILIMAP_INVALID_ALIAS_ID);
        assertThat(diagnostics.all().get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void rejectsReservedWordAsRuleId() {
        assertThat(IlimapIdentifierRules.isValidSymbolId("rule")).isFalse();
        assertThat(IlimapIdentifierRules.isValidSymbolId("input")).isFalse();
        assertThat(IlimapIdentifierRules.isValidSymbolId("output")).isFalse();

        var diagnostics = new DiagnosticCollector();
        IlimapIdentifierRules.requireSymbolId("rule", diagnostics, DUMMY_RANGE);
        assertThat(diagnostics.all()).hasSize(1);
        assertThat(diagnostics.all().get(0).code()).isEqualTo(DiagnosticCode.ILIMAP_RESERVED_WORD);
    }

    @Test
    void acceptsValidSymbolIds() {
        assertThat(IlimapIdentifierRules.isValidSymbolId("r1")).isTrue();
        assertThat(IlimapIdentifierRules.isValidSymbolId("MyRule")).isTrue();
        assertThat(IlimapIdentifierRules.isValidSymbolId("rule_1")).isTrue();
        assertThat(IlimapIdentifierRules.isValidSymbolId("A")).isTrue();
    }

    @Test
    void acceptsValidAliasIds() {
        assertThat(IlimapIdentifierRules.isValidAliasId("p")).isTrue();
        assertThat(IlimapIdentifierRules.isValidAliasId("src1")).isTrue();
        assertThat(IlimapIdentifierRules.isValidAliasId("my_alias")).isTrue();
    }

    @Test
    void rejectsInvalidPatterns() {
        assertThat(IlimapIdentifierRules.isValidSymbolId("1rule")).isFalse();
        assertThat(IlimapIdentifierRules.isValidSymbolId("")).isFalse();
        assertThat(IlimapIdentifierRules.isValidSymbolId(null)).isFalse();
        assertThat(IlimapIdentifierRules.isValidAliasId("1alias")).isFalse();
    }
}
