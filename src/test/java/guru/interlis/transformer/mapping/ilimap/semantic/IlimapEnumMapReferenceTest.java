package guru.interlis.transformer.mapping.ilimap.semantic;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import org.junit.jupiter.api.Test;

import java.util.List;

class IlimapEnumMapReferenceTest {

    private final IlimapSemanticValidator validator = new IlimapSemanticValidator();

    private IlimapSemanticResult validate(String source) {
        IlimapDocument doc = new IlimapParser(source).parseDocument();
        return validator.validate(doc);
    }

    @Test
    void acceptsSymbolicEnumMapReference() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum MyEnum {
                    "a" => "x";
                    "b" => "y";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      attr = enumMap(s.val, MyEnum);
                    }
                  }
                }
                """);

        assertThat(errorsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_ENUM_MAP)).isEmpty();
        assertThat(warningsWithCode(result, DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF)).isEmpty();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsStringEnumMapReferenceWithWarning() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum MyEnum {
                    "a" => "x";
                    "b" => "y";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      attr = enumMap(s.val, "MyEnum");
                    }
                  }
                }
                """);

        assertThat(result.hasErrors()).isFalse();
        assertThat(warningsWithCode(result, DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF)).hasSize(1);
        assertThat(warningsWithCode(result, DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF).get(0).message())
                .contains("MyEnum");
    }

    @Test
    void rejectsUnknownEnumMapReference() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      attr = enumMap(s.val, NonExistentEnum);
                    }
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(errorsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_ENUM_MAP))
                .anyMatch(d -> d.message().contains("NonExistentEnum"));
    }

    @Test
    void rejectsUnknownStringEnumMapReference() {
        var result = validate("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      attr = enumMap(s.val, "GhostEnum");
                    }
                  }
                }
                """);

        assertThat(result.hasErrors()).isTrue();
        assertThat(warningsWithCode(result, DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF)).hasSize(1);
        assertThat(errorsWithCode(result, DiagnosticCode.ILIMAP_UNKNOWN_ENUM_MAP))
                .anyMatch(d -> d.message().contains("GhostEnum"));
    }

    private static List<Diagnostic> errorsWithCode(IlimapSemanticResult result, String code) {
        return result.diagnostics().stream()
                .filter(d -> d.code().equals(code) && d.severity() == Severity.ERROR)
                .toList();
    }

    private static List<Diagnostic> warningsWithCode(IlimapSemanticResult result, String code) {
        return result.diagnostics().stream()
                .filter(d -> d.code().equals(code) && d.severity() == Severity.WARNING)
                .toList();
    }
}
