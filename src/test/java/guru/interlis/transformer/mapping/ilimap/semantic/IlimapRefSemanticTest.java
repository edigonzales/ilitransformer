package guru.interlis.transformer.mapping.ilimap.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapRefSemanticTest {

    private final IlimapSemanticValidator validator = new IlimapSemanticValidator();

    @Test
    void acceptsValidRefLongForm() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                  rule r2 {
                    target out class "M.B";
                    source p from src class "M.B";
                    ref Entstehung {
                      association "Entstehung_LFP3";
                      role "Entstehung";
                      required;
                      target rule r1 sourceRef p.Entstehung;
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsUnknownTargetRule() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    ref Foo {
                      target rule nonexistent sourceRef s.FK;
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_RULE))
                .anyMatch(d -> d.message().contains("nonexistent"));
    }

    @Test
    void acceptsRefWithoutAssociationRole() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                  rule r2 {
                    target out class "M.B";
                    source p from src class "M.B";
                    ref Foo {
                      target rule r1 sourceRef p.FK;
                    }
                  }
                }
                """);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsJoinWithValidAliases() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    source t from src class "M.B";
                    join inner s to t on eq(s.FK, t);
                  }
                }
                """);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsJoinWithUnknownAlias() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    join inner s to unknown on eq(s.FK, unknown);
                  }
                }
                """);
        assertThat(result.hasErrors()).isTrue();
        assertThat(diagnosticsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_PARENT_ALIAS))
                .anyMatch(d -> d.message().contains("unknown"));
    }

    private IlimapSemanticResult validate(String source) {
        IlimapDocument doc = new IlimapParser(source).parseDocument();
        return validator.validate(doc);
    }

    private static List<Diagnostic> diagnosticsWithCode(IlimapSemanticResult result, String code) {
        return result.diagnostics().stream().filter(d -> d.code().equals(code)).toList();
    }
}
