package guru.interlis.transformer.mapping.ilimap.semantic;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapSemanticValidatorTest {

    private final IlimapSemanticValidator validator = new IlimapSemanticValidator();

    private IlimapDocument parse(String source) {
        return new IlimapParser(source).parseDocument();
    }

    private IlimapSemanticResult validate(String source) {
        return validator.validate(parse(source));
    }

    @Test
    void acceptsMinimalValidMapping() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.symbols().resolveInput("src")).isPresent();
        assertThat(result.symbols().resolveOutput("out")).isPresent();
        assertThat(result.symbols().resolveRule("r1")).isPresent();
    }

    @Test
    void rejectsDuplicateInputIds() {
        var result = validate("""
                mapping v2 {
                  input src { path "in1.xtf"; model "M"; }
                  input src { path "in2.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_DUPLICATE_ID))
                .anyMatch(d -> d.message().contains("src"));
    }

    @Test
    void rejectsDuplicateRuleIds() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                  rule r1 {
                    target out class "M.B";
                    source s from src class "M.B";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_DUPLICATE_ID))
                .anyMatch(d -> d.message().contains("r1"));
    }

    @Test
    void rejectsUnknownTargetOutput() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target nonexistent class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_OUTPUT))
                .anyMatch(d -> d.message().contains("nonexistent"));
    }

    @Test
    void rejectsUnknownSourceInput() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from nonexistent class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_INPUT))
                .anyMatch(d -> d.message().contains("nonexistent"));
    }

    @Test
    void assignOverridesDefaultEvenIfSameAttribute() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      attr = s.val;
                    }
                    defaults {
                      attr = "fallback";
                    }
                  }
                }
                """);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsDuplicateAssignmentsInSameBlock() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      attr = s.val1;
                      attr = s.val2;
                    }
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_DUPLICATE_ASSIGNMENT))
                .anyMatch(d -> d.message().contains("attr"));
    }

    @Test
    void rejectsMissingInput() {
        var result = validate("""
                mapping v2 {
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_MISSING_INPUT))
                .isNotEmpty();
    }

    @Test
    void rejectsMissingOutput() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  rule r1 {
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_MISSING_OUTPUT))
                .isNotEmpty();
    }

    @Test
    void rejectsMissingRule() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_MISSING_RULE))
                .isNotEmpty();
    }

    @Test
    void rejectsInvalidFailPolicy() {
        var result = validate("""
                mapping v2 {
                  job {
                    failPolicy invalid;
                  }
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_INVALID_STRATEGY))
                .anyMatch(d -> d.message().contains("failPolicy"));
    }

    @Test
    void rejectsReservedOidStrategy() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  oid {
                    strategy external;
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_RESERVED_STRATEGY))
                .anyMatch(d -> d.message().contains("external"));
    }

    @Test
    void rejectsReservedBasketStrategy() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  basket expression;
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_RESERVED_STRATEGY))
                .anyMatch(d -> d.message().contains("expression"));
    }

    @Test
    void acceptsValidStrategies() {
        var result = validate("""
                mapping v2 {
                  job {
                    failPolicy strict;
                    compileMode compatible;
                  }
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  oid {
                    strategy uuid;
                  }
                  basket preserve;
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingTargetInRule() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    source s from src class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_MISSING_TARGET))
                .isNotEmpty();
    }

    @Test
    void rejectsMissingSourceInRule() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_MISSING_SOURCE))
                .isNotEmpty();
    }

    @Test
    void rejectsDuplicateSourceAliases() {
        var result = validate("""
                mapping v2 {
                  input s1 { path "in1.xtf"; model "M"; }
                  input s2 { path "in2.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source p from s1 class "M.A";
                    source p from s2 class "M.B";
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_DUPLICATE_ALIAS))
                .anyMatch(d -> d.message().contains("p"));
    }

    @Test
    void rejectsDuplicateDefaultsInSameBlock() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    defaults {
                      attr = "v1";
                      attr = "v2";
                    }
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_DUPLICATE_ASSIGNMENT))
                .anyMatch(d -> d.message().contains("attr") && d.message().contains("defaults"));
    }

    private static List<Diagnostic> diagnosticsWithCode(IlimapSemanticResult result, String code) {
        return result.diagnostics().stream().filter(d -> d.code().equals(code)).toList();
    }
}
