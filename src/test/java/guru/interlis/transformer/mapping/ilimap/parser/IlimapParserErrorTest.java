package guru.interlis.transformer.mapping.ilimap.parser;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser.ParseException;

import org.junit.jupiter.api.Test;

class IlimapParserErrorTest {

    @Test
    void rejectsUnknownTopLevelElement() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  unknown_block { }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("RBRACE");
    }

    @Test
    void rejectsUnknownRuleElement() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    unknown_element;
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("unexpected");
    }

    @Test
    void rejectsMissingSemicolonInInput() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s {
                    path "in.xtf"
                    model "M";
                  }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class);
    }

    @Test
    void rejectsMissingSemicolonInJob() {
        var parser = new IlimapParser("""
                mapping v2 {
                  job {
                    direction dm01-to-dmav
                    failPolicy strict;
                  }
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class);
    }

    @Test
    void rejectsNonV2Mapping() {
        var parser = new IlimapParser("""
                mapping v1 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("v2");
    }

    @Test
    void rejectsMissingMappingKeyword() {
        var parser = new IlimapParser("""
                input s { path "in.xtf"; model "M"; }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("mapping");
    }

    @Test
    void rejectsMissingClosingBrace() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class);
    }

    @Test
    void rejectsMissingTargetClass() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o;
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class);
    }

    @Test
    void rejectsUnknownFieldInJobBlock() {
        var parser = new IlimapParser("""
                mapping v2 {
                  job {
                    unknownField value;
                  }
                  input s { path "in.xtf"; model "M"; }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void rejectsEmptyMapping() {
        var parser = new IlimapParser("");

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class);
    }

    @Test
    void parseExceptionHasPosition() {
        var parser = new IlimapParser("""
                mapping v1 {
                  input s { path "in.xtf"; model "M"; }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("at line 1");
    }

    @Test
    void rejectsUnknownFieldInInputBlock() {
        var parser = new IlimapParser("""
                mapping v2 {
                  input s {
                    unknown "value";
                    model "M";
                  }
                  output o { path "out.xtf"; model "M"; }
                  rule r1 {
                    target o class "M.A";
                    source p from s class "M.A";
                  }
                }
                """);

        assertThatThrownBy(parser::parseDocument)
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("unexpected");
    }
}
