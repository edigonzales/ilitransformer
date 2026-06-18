package guru.interlis.transformer.mapping.ilimap.format;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import org.junit.jupiter.api.Test;

class IlimapFormatterTest {

    private final IlimapFormatter formatter = new IlimapFormatter();

    @Test
    void formatsMinimalMapping() {
        var doc = parse("""
                mapping v2 "test" {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign { Name = s.Name; }
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).isEqualTo("""
                mapping v2 "test" {
                  input src {
                    path "in.xtf";
                    model "M";
                    format xtf;
                  }

                  output out {
                    path "out.xtf";
                    model "M";
                    format xtf;
                  }

                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";

                    assign {
                      Name = s.Name;
                    }
                  }
                }
                """);
    }

    @Test
    void formatsFullRuleWithBagAndRef() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    source t from src class "M.B";
                    join inner s to t on eq(s.FK, t);
                    where s.Active == true;
                    identity s.Id;
                    assign {
                      Name = s.Name;
                    }
                    bag Positions {
                      from pos in src class "M.Pos"
                        where refEquals(pos.FK, s);
                      structure "M.PosStruc";
                      mode embed;
                      maxItems 1;
                      parentRef attribute "Pos_von" parent s;
                      assign {
                        X = pos.X;
                      }
                    }
                    ref Entstehung {
                      association "Entstehung_LFP3";
                      role "Entstehung";
                      required;
                      target rule r1 sourceRef s.Entstehung;
                    }
                    create class "M.Extra" {
                      assign {
                        Val = s.SomeField;
                      }
                    }
                    loss {
                      sourcePath s.SymbolOri;
                      reasonCode "not-representable";
                      description "Orientierung nicht abbildbar.";
                      when defined(s.SymbolOri);
                    }
                    metadata {
                      direction forward;
                      roundtrip notGuaranteed;
                      lossiness minor;
                    }
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("join inner s to t on eq(s.FK, t);");
        assertThat(result).contains("bag Positions {");
        assertThat(result).contains("from pos in src class \"M.Pos\" where refEquals(pos.FK, s);");
        assertThat(result).contains("structure \"M.PosStruc\";");
        assertThat(result).contains("mode embed;");
        assertThat(result).contains("maxItems 1;");
        assertThat(result).contains("parentRef attribute \"Pos_von\" parent s;");
        assertThat(result).contains("ref Entstehung {");
        assertThat(result).contains("association \"Entstehung_LFP3\";");
        assertThat(result).contains("role \"Entstehung\";");
        assertThat(result).contains("required;");
        assertThat(result).contains("target rule r1 sourceRef s.Entstehung;");
        assertThat(result).contains("create class \"M.Extra\" {");
        assertThat(result).contains("loss {");
        assertThat(result).contains("sourcePath s.SymbolOri;");
        assertThat(result).contains("reasonCode \"not-representable\";");
        assertThat(result).contains("when defined(s.SymbolOri);");
        assertThat(result).contains("metadata {");
        assertThat(result).contains("direction forward;");
        assertThat(result).contains("roundtrip notGuaranteed;");
        assertThat(result).contains("lossiness minor;");
    }

    @Test
    void doesNotAlignAssignmentsByDefault() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                      LongAttributeName = s.LongAttributeName;
                    }
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("      X = s.X;\n");
        assertThat(result).contains("      LongAttributeName = s.LongAttributeName;\n");
        assertThat(result).doesNotContain("X                  =");
    }

    @Test
    void preservesExpressionTextExceptTrimming() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      Desc = replace(s.Text, ";", ",");
                    }
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("Desc = replace(s.Text, \";\", \",\");");
    }

    @Test
    void formatsOidWithNamespace() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  oid { strategy deterministicUuid; namespace "http://example.com/"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("  oid {\n");
        assertThat(result).contains("    strategy deterministicUuid;\n");
        assertThat(result).contains("    namespace \"http://example.com/\";\n");
        assertThat(result).contains("  }\n");
    }

    @Test
    void formatsEnumEntries() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum StatusMap {
                    "active" => true;
                    "inactive" => false;
                    null => "unknown";
                    42 => "found";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("\"active\" => true;");
        assertThat(result).contains("\"inactive\" => false;");
        assertThat(result).contains("null => \"unknown\";");
        assertThat(result).contains("42 => \"found\";");
    }

    @Test
    void formatsJobWithAllFields() {
        var doc = parse("""
                mapping v2 {
                  job {
                    name "test-job";
                    description "A test job";
                    direction forward;
                    failPolicy strict;
                    compileMode compatible;
                    modeldir "https://models.geo.admin.ch/";
                    modeldir "https://models.example.com/";
                  }
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("name \"test-job\";");
        assertThat(result).contains("description \"A test job\";");
        assertThat(result).contains("direction forward;");
        assertThat(result).contains("failPolicy strict;");
        assertThat(result).contains("compileMode compatible;");
        String[] lines = result.split("\n");
        long modeldirCount = java.util.Arrays.stream(lines)
                .filter(l -> l.strip().startsWith("modeldir"))
                .count();
        assertThat(modeldirCount).isEqualTo(2);
    }

    @Test
    void formatsDefaults() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  defaults {
                    Beschreibung = "";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    defaults {
                      X = 0;
                    }
                    assign {
                      Name = s.Name;
                    }
                  }
                }
                """);
        String result = formatter.format(doc);
        long defaultsCount =
                result.lines().filter(l -> l.strip().equals("defaults {")).count();
        assertThat(defaultsCount).isEqualTo(2);
    }

    @Test
    void formatsSourceWithMultipleInputs() {
        var doc = parse("""
                mapping v2 {
                  input a { path "a.xtf"; model "M"; }
                  input b { path "b.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from a, b class "M.A";
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("source s from a, b class \"M.A\";");
    }

    @Test
    void formatsNestedBags() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag Outer {
                      from o in src class "M.Outer";
                      parentRef attribute "Outer_von" parent s;
                      assign {
                        X = o.X;
                      }
                      bag Inner {
                        from i in src class "M.Inner";
                        parentRef attribute "Inner_von" parent o;
                        assign {
                          Y = i.Y;
                        }
                      }
                    }
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("bag Outer {");
        assertThat(result).contains("bag Inner {");
        assertThat(result).contains("parentRef attribute \"Inner_von\" parent o;");
    }

    @Test
    void formatsBasketStatement() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  basket preserve;
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).contains("  basket preserve;\n");
    }

    @Test
    void formatsMappingWithoutName() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).startsWith("mapping v2 {\n");
    }

    @Test
    void outputEndsWithFinalNewline() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);
        String result = formatter.format(doc);
        assertThat(result).endsWith("}\n");
    }

    @Test
    void noFinalNewlineWhenDisabled() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """);
        var opts = new IlimapFormatOptions(2, false, false);
        String result = formatter.format(doc, opts);
        assertThat(result).endsWith("}");
    }

    private IlimapDocument parse(String source) {
        return new IlimapParser(source).parseDocument();
    }
}
