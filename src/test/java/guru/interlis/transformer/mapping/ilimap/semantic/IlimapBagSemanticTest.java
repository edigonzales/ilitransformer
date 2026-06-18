package guru.interlis.transformer.mapping.ilimap.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapBagSemanticTest {

    private final IlimapSemanticValidator validator = new IlimapSemanticValidator();

    @Test
    void acceptsValidBag() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag Textposition {
                      from pos in src class "M.Pos"
                        where refEquals(pos.FK, s);
                      structure "M.PosStruc";
                      mode embed;
                      maxItems 1;
                      assign {
                        Position = pos.Pos;
                      }
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsBagAliasWithHyphen() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from my-pos in src class "M.Pos";
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_INVALID_ALIAS_ID))
                .anyMatch(d -> d.message().contains("my-pos"));
    }

    @Test
    void rejectsUnknownBagInput() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from pos in unknown class "M.Pos";
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_INPUT))
                .anyMatch(d -> d.message().contains("unknown"));
    }

    @Test
    void rejectsUnknownParentAlias() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from pos in src class "M.Pos";
                      parentRef attribute "FK" parent nonexistent;
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_PARENT_ALIAS))
                .anyMatch(d -> d.message().contains("nonexistent"));
    }

    @Test
    void rejectsMaxItemsZero() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from pos in src class "M.Pos";
                      maxItems 0;
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_INVALID_MAX_ITEMS))
                .anyMatch(d -> d.message().contains("maxItems"));
    }

    @Test
    void rejectsDuplicateBagIds() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from a in src class "M.A1";
                    }
                    bag B {
                      from b in src class "M.A2";
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_DUPLICATE_ELEMENT))
                .anyMatch(d -> d.message().contains("duplicate bag"));
    }

    @Test
    void acceptsNestedBagWithParentScope() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag Outer {
                      from o in src class "M.Outer";
                      parentRef attribute "Outer_von" parent s;
                      bag Inner {
                        from i in src class "M.Inner";
                        parentRef attribute "Inner_von" parent o;
                      }
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingBagFrom() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      mode embed;
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_MISSING_BAG_FROM))
                .isNotEmpty();
    }

    private IlimapSemanticResult validate(String source) {
        IlimapDocument doc = new IlimapParser(source).parseDocument();
        return validator.validate(doc);
    }

    private static List<Diagnostic> diagnosticsWithCode(IlimapSemanticResult result, String code) {
        return result.diagnostics().stream().filter(d -> d.code().equals(code)).toList();
    }
}
