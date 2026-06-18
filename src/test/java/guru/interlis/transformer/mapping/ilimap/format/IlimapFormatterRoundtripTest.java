package guru.interlis.transformer.mapping.ilimap.format;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapFormatterRoundtripTest {

    private final IlimapFormatter formatter = new IlimapFormatter();

    @Test
    void parseFormatParseKeepsEquivalentAst() {
        String source = """
                mapping v2 "roundtrip" {
                  job {
                    direction forward;
                    failPolicy strict;
                    compileMode compatible;
                  }
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
                  oid { strategy uuid; }
                  basket preserve;
                  enum StatusMap {
                    "active" => true;
                    "inactive" => false;
                  }
                  rule r1 {
                    target out class "M.T.A";
                    source s from src class "M.T.B";
                    where s.Active == true;
                    identity s.Id, s.SubId;
                    assign {
                      Name = s.Name;
                      Desc = coalesce(s.Desc, "");
                    }
                  }
                }
                """;
        IlimapDocument doc1 = parse(source);
        String formatted = formatter.format(doc1);
        IlimapDocument doc2 = parse(formatted);

        assertThat(doc2.formatVersion()).isEqualTo(doc1.formatVersion());
        assertThat(doc2.name()).isEqualTo(doc1.name());

        assertThat(doc2.job()).isNotNull();
        assertThat(doc2.job().direction()).isEqualTo(doc1.job().direction());
        assertThat(doc2.job().failPolicy()).isEqualTo(doc1.job().failPolicy());
        assertThat(doc2.job().compileMode()).isEqualTo(doc1.job().compileMode());

        assertThat(doc2.inputs()).hasSize(doc1.inputs().size());
        assertThat(doc2.inputs().get(0).id()).isEqualTo("src");
        assertThat(doc2.inputs().get(0).path()).isEqualTo("in.xtf");
        assertThat(doc2.inputs().get(0).model()).isEqualTo("M");
        assertThat(doc2.inputs().get(0).format()).isEqualTo("xtf");

        assertThat(doc2.outputs()).hasSize(doc1.outputs().size());
        assertThat(doc2.outputs().get(0).id()).isEqualTo("out");

        assertThat(doc2.oid()).isNotNull();
        assertThat(doc2.oid().strategy()).isEqualTo("uuid");
        assertThat(doc2.basket()).isNotNull();
        assertThat(doc2.basket().strategy()).isEqualTo("preserve");

        assertThat(doc2.enums()).hasSize(1);
        assertThat(doc2.enums().get(0).id()).isEqualTo("StatusMap");
        assertThat(doc2.enums().get(0).entries()).hasSize(2);

        assertThat(doc2.rules()).hasSize(1);
        IlimapRuleBlock rule = doc2.rules().get(0);
        assertThat(rule.id()).isEqualTo("r1");

        IlimapTargetStmt target = ruleElement(rule, IlimapTargetStmt.class);
        assertThat(target.outputId()).isEqualTo("out");
        assertThat(target.targetClass()).isEqualTo("M.T.A");

        IlimapSourceStmt src = ruleElement(rule, IlimapSourceStmt.class);
        assertThat(src.alias()).isEqualTo("s");
        assertThat(src.sourceClass()).isEqualTo("M.T.B");

        IlimapWhereStmt where = ruleElement(rule, IlimapWhereStmt.class);
        assertThat(where.expression().text()).isEqualTo("s.Active == true");

        IlimapIdentityStmt identity = ruleElement(rule, IlimapIdentityStmt.class);
        assertThat(identity.expressions()).hasSize(2);

        IlimapAssignmentBlock assign = ruleElement(rule, IlimapAssignmentBlock.class);
        assertThat(assign.assignments()).hasSize(2);
        assertThat(assign.assignments().get(0).targetAttribute()).isEqualTo("Name");
        assertThat(assign.assignments().get(0).expression().text()).isEqualTo("s.Name");
    }

    @Test
    void formatIsStableWhenRunTwice() {
        String source = """
                mapping v2 "stable" {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  oid { strategy uuid; namespace "http://example.com/"; }
                  basket preserve;
                  enum E1 { "a" => "b"; }
                  defaults { X = 0; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    where s.Active == true;
                    identity s.Id;
                    assign { Name = s.Name; Desc = replace(s.Desc, ";", ","); }
                  }
                }
                """;
        IlimapDocument doc1 = parse(source);
        String formatted1 = formatter.format(doc1);
        IlimapDocument doc2 = parse(formatted1);
        String formatted2 = formatter.format(doc2);
        assertThat(formatted2).isEqualTo(formatted1);
    }

    @Test
    void roundtripWithMinimalLfp3Fixture() throws IOException {
        Path fixture = Path.of("src/test/resources/mapping/ilimap/minimal-lfp3.ilimap");
        String source = Files.readString(fixture);
        IlimapDocument doc1 = parse(source);
        String formatted1 = formatter.format(doc1);
        IlimapDocument doc2 = parse(formatted1);
        String formatted2 = formatter.format(doc2);
        assertThat(formatted2).isEqualTo(formatted1);

        assertThat(doc2.name()).isEqualTo("minimal-lfp3");
        assertThat(doc2.rules()).hasSize(1);
        assertThat(doc2.rules().get(0).id()).isEqualTo("lfp3");
    }

    @Test
    void roundtripWithFullRuleStructure() {
        String source = """
                mapping v2 "full" {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    source t from src class "M.B";
                    join inner s to t on eq(s.FK, t);
                    assign {
                      Name = s.Name;
                    }
                    bag B1 {
                      from b in src class "M.B"
                        where refEquals(b.FK, s);
                      structure "M.Struc";
                      mode embed;
                      maxItems 5;
                      parentRef role "Parent" parent s;
                      assign {
                        V = b.V;
                      }
                      bag Nested {
                        from n in src class "M.N";
                        parentRef attribute "N_von" parent b;
                      }
                    }
                    ref Ref1 {
                      association "Assoc";
                      role "MyRole";
                      required;
                      target rule r1 sourceRef s.FK;
                    }
                    create class "M.Extra" {
                      assign {
                        ExtraVal = s.SomeField;
                      }
                    }
                    loss {
                      sourcePath s.Ori;
                      reasonCode "not-mapped";
                      description "Cannot map orientation.";
                      when defined(s.Ori);
                    }
                    metadata {
                      direction forward;
                      roundtrip notGuaranteed;
                      lossiness minor;
                    }
                  }
                }
                """;
        IlimapDocument doc1 = parse(source);
        String formatted1 = formatter.format(doc1);
        IlimapDocument doc2 = parse(formatted1);
        String formatted2 = formatter.format(doc2);
        assertThat(formatted2).isEqualTo(formatted1);

        IlimapRuleBlock rule = doc2.rules().get(0);
        assertThat(ruleElements(rule, IlimapJoinStmt.class)).hasSize(1);
        assertThat(ruleElements(rule, IlimapBagBlock.class)).hasSize(1);
        assertThat(ruleElements(rule, IlimapRefBlock.class)).hasSize(1);
        assertThat(ruleElements(rule, IlimapCreateBlock.class)).hasSize(1);
        assertThat(ruleElements(rule, IlimapLossBlock.class)).hasSize(1);
        assertThat(ruleElements(rule, IlimapMetadataBlock.class)).hasSize(1);

        IlimapBagBlock bag = ruleElement(rule, IlimapBagBlock.class);
        assertThat(bag.nestedBags()).hasSize(1);
        assertThat(bag.nestedBags().get(0).id()).isEqualTo("Nested");
    }

    private IlimapDocument parse(String source) {
        return new IlimapParser(source).parseDocument();
    }

    @SuppressWarnings("unchecked")
    private <T extends IlimapRuleElement> T ruleElement(IlimapRuleBlock rule, Class<T> type) {
        return rule.elements().stream()
                .filter(type::isInstance)
                .map(e -> (T) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no element of type " + type.getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    private <T extends IlimapRuleElement> List<T> ruleElements(IlimapRuleBlock rule, Class<T> type) {
        return rule.elements().stream().filter(type::isInstance).map(e -> (T) e).toList();
    }
}
