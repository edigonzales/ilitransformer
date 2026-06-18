package guru.interlis.transformer.mapping.ilimap.parser;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IlimapParserFullRuleTest {

    @Test
    void parsesJoinInner() {
        var doc = parse("""
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
        List<IlimapRuleElement> elements = doc.rules().get(0).elements();
        IlimapJoinStmt join = elements.stream()
                .filter(e -> e instanceof IlimapJoinStmt)
                .map(e -> (IlimapJoinStmt) e)
                .findFirst().orElseThrow();
        assertThat(join.joinType()).isEqualTo("inner");
        assertThat(join.leftAlias()).isEqualTo("s");
        assertThat(join.rightAlias()).isEqualTo("t");
        assertThat(join.on().text()).isEqualTo("eq(s.FK, t)");
    }

    @Test
    void parsesJoinLeft() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    source t from src class "M.B";
                    join left s to t on eq(s.FK, t);
                  }
                }
                """);
        IlimapJoinStmt join = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapJoinStmt)
                .map(e -> (IlimapJoinStmt) e)
                .findFirst().orElseThrow();
        assertThat(join.joinType()).isEqualTo("left");
    }

    @Test
    void parsesBagWithFromStructureModeMaxItemsAssign() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag Textposition {
                      from pos in src class "M.Pos"
                        where refEquals(pos.FK, s);
                      structure "M.TextposStruc";
                      mode embed;
                      maxItems 1;
                      assign {
                        Position = pos.Pos;
                        Ori = pos.Ori;
                      }
                    }
                  }
                }
                """);
        IlimapBagBlock bag = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapBagBlock)
                .map(e -> (IlimapBagBlock) e)
                .findFirst().orElseThrow();
        assertThat(bag.id()).isEqualTo("Textposition");
        assertThat(bag.from()).isNotNull();
        assertThat(bag.from().alias()).isEqualTo("pos");
        assertThat(bag.from().inputId()).isEqualTo("src");
        assertThat(bag.from().sourceClass()).isEqualTo("M.Pos");
        assertThat(bag.from().where()).isNotNull();
        assertThat(bag.from().where().text()).isEqualTo("refEquals(pos.FK, s)");
        assertThat(bag.structure()).isEqualTo("M.TextposStruc");
        assertThat(bag.mode()).isEqualTo("embed");
        assertThat(bag.maxItems()).isEqualTo(1);
        assertThat(bag.assign()).isNotNull();
        assertThat(bag.assign().assignments()).hasSize(2);
    }

    @Test
    void parsesNestedBags() {
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
        IlimapBagBlock outer = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapBagBlock)
                .map(e -> (IlimapBagBlock) e)
                .findFirst().orElseThrow();
        assertThat(outer.id()).isEqualTo("Outer");
        assertThat(outer.nestedBags()).hasSize(1);
        IlimapBagBlock inner = outer.nestedBags().get(0);
        assertThat(inner.id()).isEqualTo("Inner");
        assertThat(inner.from().alias()).isEqualTo("i");
        assertThat(inner.parentRef()).isNotNull();
        assertThat(inner.parentRef().kind()).isEqualTo("attribute");
        assertThat(inner.parentRef().name()).isEqualTo("Inner_von");
        assertThat(inner.parentRef().parentAlias()).isEqualTo("o");
    }

    @Test
    void parsesBagWithParentRef() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from b in src class "M.B";
                      parentRef role "MyRole" parent s;
                    }
                  }
                }
                """);
        IlimapBagBlock bag = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapBagBlock)
                .map(e -> (IlimapBagBlock) e)
                .findFirst().orElseThrow();
        assertThat(bag.parentRef().kind()).isEqualTo("role");
        assertThat(bag.parentRef().name()).isEqualTo("MyRole");
        assertThat(bag.parentRef().parentAlias()).isEqualTo("s");
    }

    @Test
    void parsesBagWithModeExpand() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from b in src class "M.B";
                      mode expand;
                    }
                  }
                }
                """);
        IlimapBagBlock bag = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapBagBlock)
                .map(e -> (IlimapBagBlock) e)
                .findFirst().orElseThrow();
        assertThat(bag.mode()).isEqualTo("expand");
    }

    @Test
    void parsesRefLongForm() {
        var doc = parse("""
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
        IlimapRefBlock ref = doc.rules().get(1).elements().stream()
                .filter(e -> e instanceof IlimapRefBlock)
                .map(e -> (IlimapRefBlock) e)
                .findFirst().orElseThrow();
        assertThat(ref.id()).isEqualTo("Entstehung");
        assertThat(ref.association()).isEqualTo("Entstehung_LFP3");
        assertThat(ref.role()).isEqualTo("Entstehung");
        assertThat(ref.required()).isTrue();
        assertThat(ref.targetRuleId()).isEqualTo("r1");
        assertThat(ref.sourceRef().text()).isEqualTo("p.Entstehung");
    }

    @Test
    void rejectsRefShortForm() {
        assertThatThrownBy(() -> parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    ref Foo -> r2 using s.FK {
                      association "Assoc";
                    }
                  }
                }
                """))
                .isInstanceOf(IlimapParser.ParseException.class)
                .hasMessageContaining("short form")
                .hasMessageContaining("->");
    }

    @Test
    void parsesCreateBlock() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    create class "M.Extra" {
                      assign {
                        ExtraAttr = s.SomeField;
                      }
                    }
                  }
                }
                """);
        IlimapCreateBlock create = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapCreateBlock)
                .map(e -> (IlimapCreateBlock) e)
                .findFirst().orElseThrow();
        assertThat(create.targetClass()).isEqualTo("M.Extra");
        assertThat(create.assign()).isNotNull();
        assertThat(create.assign().assignments()).hasSize(1);
        assertThat(create.assign().assignments().get(0).targetAttribute()).isEqualTo("ExtraAttr");
    }

    @Test
    void parsesCreateBlockWithoutAssign() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    create class "M.Extra" {
                    }
                  }
                }
                """);
        IlimapCreateBlock create = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapCreateBlock)
                .map(e -> (IlimapCreateBlock) e)
                .findFirst().orElseThrow();
        assertThat(create.targetClass()).isEqualTo("M.Extra");
        assertThat(create.assign()).isNull();
    }

    @Test
    void parsesLossBlock() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    loss {
                      sourcePath s.SymbolOri;
                      reasonCode "not-representable";
                      description "Orientierung nicht abbildbar.";
                      when defined(s.SymbolOri);
                    }
                  }
                }
                """);
        IlimapLossBlock loss = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapLossBlock)
                .map(e -> (IlimapLossBlock) e)
                .findFirst().orElseThrow();
        assertThat(loss.sourcePath().text()).isEqualTo("s.SymbolOri");
        assertThat(loss.reasonCode()).isEqualTo("not-representable");
        assertThat(loss.description()).isEqualTo("Orientierung nicht abbildbar.");
        assertThat(loss.when().text()).isEqualTo("defined(s.SymbolOri)");
    }

    @Test
    void parsesMetadataBlock() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    metadata {
                      direction forward;
                      roundtrip notGuaranteed;
                      lossiness minor;
                    }
                  }
                }
                """);
        IlimapMetadataBlock meta = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapMetadataBlock)
                .map(e -> (IlimapMetadataBlock) e)
                .findFirst().orElseThrow();
        assertThat(meta.direction()).isEqualTo("forward");
        assertThat(meta.roundtrip()).isEqualTo("notGuaranteed");
        assertThat(meta.lossiness()).isEqualTo("minor");
    }

    @Test
    void parsesBagFromWithoutWhere() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from b in src class "M.B";
                    }
                  }
                }
                """);
        IlimapBagBlock bag = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapBagBlock)
                .map(e -> (IlimapBagBlock) e)
                .findFirst().orElseThrow();
        assertThat(bag.from().where()).isNull();
    }

    @Test
    void parsesRefWithoutAssociationAndRole() {
        var doc = parse("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    ref Foo {
                      target rule r1 sourceRef s.FK;
                    }
                  }
                }
                """);
        IlimapRefBlock ref = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapRefBlock)
                .map(e -> (IlimapRefBlock) e)
                .findFirst().orElseThrow();
        assertThat(ref.association()).isNull();
        assertThat(ref.role()).isNull();
        assertThat(ref.required()).isFalse();
        assertThat(ref.targetRuleId()).isEqualTo("r1");
    }

    private IlimapDocument parse(String source) {
        return new IlimapParser(source).parseDocument();
    }
}
