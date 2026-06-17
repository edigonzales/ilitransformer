package guru.interlis.transformer.mapping.ilimap.parser;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapParserMinimalTest {

    @Test
    void parsesMinimalMappingWithOneRule() {
        var parser = new IlimapParser("""
                mapping v2 "test-map" {
                  input src {
                    path "in.xtf";
                    model "TestModel";
                  }
                  output out {
                    path "out.xtf";
                    model "TestModel";
                  }
                  rule r1 {
                    target out class "TestModel.ClassA";
                    source s from src class "TestModel.ClassA";
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();

        assertThat(doc.formatVersion()).isEqualTo(IlimapFormatVersion.V2);
        assertThat(doc.name()).isEqualTo("test-map");
        assertThat(doc.inputs()).hasSize(1);
        assertThat(doc.inputs().get(0).id()).isEqualTo("src");
        assertThat(doc.outputs()).hasSize(1);
        assertThat(doc.outputs().get(0).id()).isEqualTo("out");
        assertThat(doc.rules()).hasSize(1);
        assertThat(doc.rules().get(0).id()).isEqualTo("r1");

        IlimapRuleBlock rule = doc.rules().get(0);
        assertThat(rule.elements()).hasSize(2);
        assertThat(rule.elements().get(0)).isInstanceOf(IlimapTargetStmt.class);
        assertThat(rule.elements().get(1)).isInstanceOf(IlimapSourceStmt.class);

        IlimapTargetStmt target = (IlimapTargetStmt) rule.elements().get(0);
        assertThat(target.outputId()).isEqualTo("out");
        assertThat(target.targetClass()).isEqualTo("TestModel.ClassA");

        IlimapSourceStmt source = (IlimapSourceStmt) rule.elements().get(1);
        assertThat(source.alias()).isEqualTo("s");
        assertThat(source.inputIds()).containsExactly("src");
        assertThat(source.sourceClass()).isEqualTo("TestModel.ClassA");
    }

    @Test
    void parsesJobInputOutputOidBasketEnum() {
        var parser = new IlimapParser("""
                mapping v2 "full" {
                  job {
                    name "my-job";
                    direction dm01-to-dmav;
                    failPolicy strict;
                    compileMode compatible;
                    modeldir "http://models.example.org/";
                  }
                  input dm01 {
                    path "input/dm01.itf";
                    model "DM01";
                    format itf;
                  }
                  output dmav {
                    path "out/dmav.xtf";
                    model "DMAV";
                    format xtf;
                  }
                  oid {
                    strategy uuid;
                    namespace "http://namespace.example.org";
                  }
                  basket shared;
                  enum MyEnum {
                    "a" => 1;
                    "b" => 2;
                  }
                  rule r1 {
                    target dmav class "DMAV.ClassA";
                    source p from dm01 class "DM01.ClassA";
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();

        assertThat(doc.job()).isNotNull();
        assertThat(doc.job().name()).isEqualTo("my-job");
        assertThat(doc.job().direction()).isEqualTo("dm01-to-dmav");
        assertThat(doc.job().failPolicy()).isEqualTo("strict");
        assertThat(doc.job().compileMode()).isEqualTo("compatible");
        assertThat(doc.job().modeldirs()).containsExactly("http://models.example.org/");

        assertThat(doc.inputs()).hasSize(1);
        IlimapInputBlock input = doc.inputs().get(0);
        assertThat(input.id()).isEqualTo("dm01");
        assertThat(input.path()).isEqualTo("input/dm01.itf");
        assertThat(input.model()).isEqualTo("DM01");
        assertThat(input.format()).isEqualTo("itf");

        assertThat(doc.outputs()).hasSize(1);
        IlimapOutputBlock output = doc.outputs().get(0);
        assertThat(output.id()).isEqualTo("dmav");
        assertThat(output.path()).isEqualTo("out/dmav.xtf");
        assertThat(output.model()).isEqualTo("DMAV");
        assertThat(output.format()).isEqualTo("xtf");

        assertThat(doc.oid()).isNotNull();
        assertThat(doc.oid().strategy()).isEqualTo("uuid");
        assertThat(doc.oid().namespace()).isEqualTo("http://namespace.example.org");

        assertThat(doc.basket()).isNotNull();
        assertThat(doc.basket().strategy()).isEqualTo("shared");

        assertThat(doc.enums()).hasSize(1);
        IlimapEnumBlock enumBlock = doc.enums().get(0);
        assertThat(enumBlock.id()).isEqualTo("MyEnum");
        assertThat(enumBlock.entries()).hasSize(2);
        assertThat(enumBlock.entries().get(0).source())
                .isEqualTo(new IlimapLiteral.StringLit(
                        "a", enumBlock.entries().get(0).source().range()));
        assertThat(enumBlock.entries().get(0).target())
                .isEqualTo(new IlimapLiteral.NumberLit(
                        "1", enumBlock.entries().get(0).target().range()));

        assertThat(doc.rules()).hasSize(1);
    }

    @Test
    void parsesRuleWithSourceWhereIdentityAssign() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                    where p.x == #VAL;
                    identity p.id, p.name;
                    assign {
                      num = p.num;
                      desc = replace(p.desc, ";", ",");
                    }
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();
        IlimapRuleBlock rule = doc.rules().get(0);

        assertThat(rule.elements()).hasSize(5);
        assertThat(rule.elements().get(0)).isInstanceOf(IlimapTargetStmt.class);
        assertThat(rule.elements().get(1)).isInstanceOf(IlimapSourceStmt.class);
        assertThat(rule.elements().get(2)).isInstanceOf(IlimapWhereStmt.class);
        assertThat(rule.elements().get(3)).isInstanceOf(IlimapIdentityStmt.class);
        assertThat(rule.elements().get(4)).isInstanceOf(IlimapAssignmentBlock.class);

        IlimapWhereStmt where = (IlimapWhereStmt) rule.elements().get(2);
        assertThat(where.expression().text()).isEqualTo("p.x == #VAL");

        IlimapIdentityStmt identity = (IlimapIdentityStmt) rule.elements().get(3);
        assertThat(identity.expressions()).hasSize(2);
        assertThat(identity.expressions().get(0).text()).isEqualTo("p.id");
        assertThat(identity.expressions().get(1).text()).isEqualTo("p.name");

        IlimapAssignmentBlock assign = (IlimapAssignmentBlock) rule.elements().get(4);
        assertThat(assign.assignments()).hasSize(2);
        assertThat(assign.assignments().get(0).targetAttribute()).isEqualTo("num");
        assertThat(assign.assignments().get(0).expression().text()).isEqualTo("p.num");
        assertThat(assign.assignments().get(1).targetAttribute()).isEqualTo("desc");
        assertThat(assign.assignments().get(1).expression().text())
                .isEqualTo("replace(p.desc, \";\", \",\")");
    }

    @Test
    void parsesAssignmentExpressionWithSemicolonInString() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                    assign {
                      x = replace(p.Text, ";", ",");
                    }
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();
        IlimapRuleBlock rule = doc.rules().get(0);
        IlimapAssignmentBlock assign =
                (IlimapAssignmentBlock) rule.elements().stream()
                        .filter(e -> e instanceof IlimapAssignmentBlock)
                        .findFirst()
                        .orElseThrow();

        assertThat(assign.assignments()).hasSize(1);
        assertThat(assign.assignments().get(0).expression().text())
                .isEqualTo("replace(p.Text, \";\", \",\")");
    }

    @Test
    void parsesRuleDefaultsBlock() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                    defaults {
                      attr = "default-value";
                    }
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();
        IlimapRuleBlock rule = doc.rules().get(0);

        assertThat(rule.elements()).hasSize(3);
        assertThat(rule.elements().get(2)).isInstanceOf(IlimapDefaultsBlock.class);

        IlimapDefaultsBlock defaults = (IlimapDefaultsBlock) rule.elements().get(2);
        assertThat(defaults.assignments()).hasSize(1);
        assertThat(defaults.assignments().get(0).targetAttribute()).isEqualTo("attr");
        assertThat(defaults.assignments().get(0).expression().text()).isEqualTo("\"default-value\"");
    }

    @Test
    void parsesTopLevelDefaultsBlock() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  defaults {
                    globalAttr = "global";
                  }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();

        assertThat(doc.defaults()).isNotNull();
        assertThat(doc.defaults().assignments()).hasSize(1);
        assertThat(doc.defaults().assignments().get(0).targetAttribute())
                .isEqualTo("globalAttr");
        assertThat(doc.defaults().assignments().get(0).expression().text())
                .isEqualTo("\"global\"");
    }

    @Test
    void parsesMappingWithoutJobBlock() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();
        assertThat(doc.job()).isNull();
    }

    @Test
    void parsesMappingWithoutName() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();
        assertThat(doc.name()).isNull();
    }

    @Test
    void parsesSourceWithMultipleInputs() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s1 { path "in1.xtf"; model "M"; }
                  input s2 { path "in2.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s1, s2 class "M.A";
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();
        IlimapRuleBlock rule = doc.rules().get(0);
        IlimapSourceStmt source = (IlimapSourceStmt) rule.elements().get(1);
        assertThat(source.inputIds()).containsExactly("s1", "s2");
    }

    @Test
    void parsesSourceWithInlineWhere() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A" where p.x > 0;
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();
        IlimapRuleBlock rule = doc.rules().get(0);
        IlimapSourceStmt source = (IlimapSourceStmt) rule.elements().get(1);
        assertThat(source.where()).isNotNull();
        assertThat(source.where().text()).isEqualTo("p.x > 0");
    }

    @Test
    void tracksSourceRanges() {
        var parser = new IlimapParser("""
                mapping v2 "test" {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        IlimapDocument doc = parser.parseDocument();

        assertThat(doc.range().start().line()).isEqualTo(1);
        assertThat(doc.range().start().column()).isEqualTo(1);

        assertThat(doc.rules().get(0).range().start().line()).isPositive();
        assertThat(doc.rules().get(0).id()).isEqualTo("r1");
    }

    @Test
    void parsesMinimalLfp3FixtureFile() throws IOException {
        String source = Files.readString(
                Path.of("src/test/resources/mapping/ilimap/minimal-lfp3.ilimap"));
        var parser = new IlimapParser(source);
        IlimapDocument doc = parser.parseDocument();

        assertThat(doc.formatVersion()).isEqualTo(IlimapFormatVersion.V2);
        assertThat(doc.name()).isEqualTo("minimal-lfp3");
        assertThat(doc.job()).isNotNull();
        assertThat(doc.job().direction()).isEqualTo("dm01-to-dmav");
        assertThat(doc.job().failPolicy()).isEqualTo("strict");
        assertThat(doc.job().compileMode()).isEqualTo("compatible");
        assertThat(doc.job().modeldirs()).containsExactly("https://models.geo.admin.ch/");

        assertThat(doc.inputs()).hasSize(1);
        assertThat(doc.inputs().get(0).id()).isEqualTo("dm01");
        assertThat(doc.inputs().get(0).format()).isEqualTo("itf");

        assertThat(doc.outputs()).hasSize(1);
        assertThat(doc.outputs().get(0).id()).isEqualTo("dmav");
        assertThat(doc.outputs().get(0).format()).isEqualTo("xtf");

        assertThat(doc.enums()).hasSize(1);
        IlimapEnumBlock enumBlock = doc.enums().get(0);
        assertThat(enumBlock.id()).isEqualTo("Zuverlaessigkeit_DM01_DMAV");
        assertThat(enumBlock.entries()).hasSize(2);

        assertThat(doc.rules()).hasSize(1);
        IlimapRuleBlock rule = doc.rules().get(0);
        assertThat(rule.id()).isEqualTo("lfp3");
        assertThat(rule.elements()).hasSize(5);

        IlimapWhereStmt where = (IlimapWhereStmt) rule.elements().get(2);
        assertThat(where.expression().text()).isEqualTo("p.LFPArt == #LFP3");

        IlimapAssignmentBlock assign = (IlimapAssignmentBlock) rule.elements().get(4);
        assertThat(assign.assignments()).hasSize(3);
    }
}
