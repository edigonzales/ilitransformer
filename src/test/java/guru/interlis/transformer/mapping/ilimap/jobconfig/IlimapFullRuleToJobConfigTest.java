package guru.interlis.transformer.mapping.ilimap.jobconfig;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticResult;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidator;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapFullRuleToJobConfigTest {

    private final IlimapToJobConfigMapper mapper = new IlimapToJobConfigMapper();

    private JobConfig mapFromSource(String source) {
        IlimapDocument doc = new IlimapParser(source).parseDocument();
        IlimapSemanticResult sem = new IlimapSemanticValidator().validate(doc);
        assertThat(sem.hasErrors())
                .as("semantic validation should pass: %s", sem.diagnostics())
                .isFalse();
        return mapper.map(doc, sem.symbols(), Path.of("."));
    }

    @Test
    void mapsJoinToJobConfig() {
        JobConfig config = mapFromSource("""
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
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.joins).hasSize(1);
        JobConfig.JoinSpec join = rule.joins.get(0);
        assertThat(join.type).isEqualTo("inner");
        assertThat(join.left).isEqualTo("s");
        assertThat(join.right).isEqualTo("t");
        assertThat(join.on).isEqualTo("eq(s.FK, t)");
    }

    @Test
    void mapsBagToJobConfig() {
        JobConfig config = mapFromSource("""
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
                        Ori = coalesce(pos.Ori, 100.0);
                      }
                    }
                  }
                }
                """);
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.bags).containsKey("Textposition");
        JobConfig.BagSpec bag = rule.bags.get("Textposition");
        assertThat(bag.from).isNotNull();
        assertThat(bag.from.alias).isEqualTo("pos");
        assertThat(bag.from.input).isEqualTo("src");
        assertThat(bag.from.clazz).isEqualTo("M.Pos");
        assertThat(bag.from.where).isEqualTo("refEquals(pos.FK, s)");
        assertThat(bag.structure).isEqualTo("M.PosStruc");
        assertThat(bag.mode).isEqualTo("embed");
        assertThat(bag.maxItems).isEqualTo(1);
        assertThat(bag.assign).containsEntry("Position", "pos.Pos");
        assertThat(bag.assign).containsEntry("Ori", "coalesce(pos.Ori, 100.0)");
    }

    @Test
    void mapsNestedBagToJobConfig() {
        JobConfig config = mapFromSource("""
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
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        JobConfig.BagSpec outer = rule.bags.get("Outer");
        assertThat(outer.parentRef).isNotNull();
        assertThat(outer.parentRef.attribute).isEqualTo("Outer_von");
        assertThat(outer.parentRef.parentAlias).isEqualTo("s");
        assertThat(outer.nestedBags).containsKey("Inner");
        JobConfig.BagSpec inner = outer.nestedBags.get("Inner");
        assertThat(inner.from.alias).isEqualTo("i");
        assertThat(inner.parentRef.attribute).isEqualTo("Inner_von");
        assertThat(inner.parentRef.parentAlias).isEqualTo("o");
        assertThat(inner.assign).containsEntry("Y", "i.Y");
    }

    @Test
    void mapsBagWithRoleParentRef() {
        JobConfig config = mapFromSource("""
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
        JobConfig.BagSpec bag = config.mapping.rules.get(0).bags.get("B");
        assertThat(bag.parentRef.role).isEqualTo("MyRole");
        assertThat(bag.parentRef.attribute).isNull();
        assertThat(bag.parentRef.parentAlias).isEqualTo("s");
    }

    @Test
    void mapsRefToJobConfig() {
        JobConfig config = mapFromSource("""
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
        JobConfig.RuleSpec rule = config.mapping.rules.get(1);
        assertThat(rule.refs).hasSize(1);
        JobConfig.RefMapping ref = rule.refs.get(0);
        assertThat(ref.target).isEqualTo("Entstehung");
        assertThat(ref.association).isEqualTo("Entstehung_LFP3");
        assertThat(ref.role).isEqualTo("Entstehung");
        assertThat(ref.required).isTrue();
        assertThat(ref.targetRule).isEqualTo("r1");
        assertThat(ref.sourceRef).isEqualTo("p.Entstehung");
        assertThat(ref.targetObject).isNotNull();
        assertThat(ref.targetObject.rule).isEqualTo("r1");
        assertThat(ref.targetObject.sourceRef).isEqualTo("p.Entstehung");
    }

    @Test
    void mapsCreateToJobConfig() {
        JobConfig config = mapFromSource("""
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
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.create).hasSize(1);
        JobConfig.CreateSpec cs = rule.create.get(0);
        assertThat(cs.clazz).isEqualTo("M.Extra");
        assertThat(cs.assign).containsEntry("ExtraAttr", "s.SomeField");
    }

    @Test
    void mapsLossToJobConfig() {
        JobConfig config = mapFromSource("""
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
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.losses).hasSize(1);
        JobConfig.LossSpec loss = rule.losses.get(0);
        assertThat(loss.sourcePath).isEqualTo("s.SymbolOri");
        assertThat(loss.reasonCode).isEqualTo("not-representable");
        assertThat(loss.description).isEqualTo("Orientierung nicht abbildbar.");
        assertThat(loss.when).isEqualTo("defined(s.SymbolOri)");
    }

    @Test
    void mapsMetadataToJobConfig() {
        JobConfig config = mapFromSource("""
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
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.metadata).isNotNull();
        assertThat(rule.metadata.direction).isEqualTo("forward");
        assertThat(rule.metadata.roundtrip).isEqualTo("notGuaranteed");
        assertThat(rule.metadata.lossiness).isEqualTo("minor");
    }

    @Test
    void maxItemsIsMappedNotIgnored() {
        JobConfig config = mapFromSource("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag B {
                      from b in src class "M.B";
                      maxItems 3;
                    }
                  }
                }
                """);
        JobConfig.BagSpec bag = config.mapping.rules.get(0).bags.get("B");
        assertThat(bag.maxItems).isEqualTo(3);
    }

    @Test
    void lossFieldsAreMappedNotIgnored() {
        JobConfig config = mapFromSource("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    loss {
                      sourcePath s.Field;
                      reasonCode "code";
                    }
                  }
                }
                """);
        JobConfig.LossSpec loss = config.mapping.rules.get(0).losses.get(0);
        assertThat(loss.sourcePath).isNotNull();
        assertThat(loss.reasonCode).isNotNull();
    }

    @Test
    void metadataFieldsAreMappedNotIgnored() {
        JobConfig config = mapFromSource("""
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    metadata {
                      direction reverse;
                    }
                  }
                }
                """);
        assertThat(config.mapping.rules.get(0).metadata).isNotNull();
        assertThat(config.mapping.rules.get(0).metadata.direction).isEqualTo("reverse");
    }
}
