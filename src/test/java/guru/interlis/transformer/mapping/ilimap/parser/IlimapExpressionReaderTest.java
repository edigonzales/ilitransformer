package guru.interlis.transformer.mapping.ilimap.parser;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapExpressionReader.ReaderException;

import org.junit.jupiter.api.Test;

class IlimapExpressionReaderTest {

    @Test
    void stopsAtPlainStatementSemicolon() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon("p.Name;", 0);

        assertThat(result.text()).isEqualTo("p.Name");
    }

    @Test
    void stopsAtTopLevelSemicolon() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon("coalesce(a, b);", 0);

        assertThat(result.text()).isEqualTo("coalesce(a, b)");
    }

    @Test
    void doesNotStopAtSemicolonInsideString() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "replace(p.Text, \";\", \",\"); next", 0);

        assertThat(result.text()).isEqualTo("replace(p.Text, \";\", \",\")");
    }

    @Test
    void ignoresSemicolonInsideString() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "x = \"hello; world\" + y;", 0);

        assertThat(result.text()).isEqualTo("x = \"hello; world\" + y");
    }

    @Test
    void doesNotStopAtSemicolonInsideFunctionArgumentString() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "concat(\"a;b\", \";c\");", 0);

        assertThat(result.text()).isEqualTo("concat(\"a;b\", \";c\")");
    }

    @Test
    void ignoresSemicolonInsideFunctionCall() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "replace(x, \";\", \":\");", 0);

        assertThat(result.text()).isEqualTo("replace(x, \";\", \":\")");
    }

    @Test
    void doesNotStopAtSemicolonInsideBlockComment() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "x /* comment with ; inside */ + 1; y", 0);

        assertThat(result.text()).doesNotContain("/*");
        assertThat(result.text()).contains("x");
        assertThat(result.text()).contains("+ 1");
    }

    @Test
    void ignoresSemicolonInsideBlockComment() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "a /* ; */ b;", 0);

        assertThat(result.text()).doesNotContain("/*");
        assertThat(result.text()).doesNotContain("*/");
        assertThat(result.text()).contains("a");
        assertThat(result.text()).contains("b");
    }

    @Test
    void ignoresSemicolonInsideLineComment() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "a // comment with ; inside\n+ b;", 0);

        assertThat(result.text()).doesNotContain("//");
        assertThat(result.text()).doesNotContain("comment");
        assertThat(result.text()).contains("a");
        assertThat(result.text()).contains("+ b");
    }

    @Test
    void doesNotStopAtSemicolonInsideNestedParentheses() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "outer(inner(a=\"test;x\", b=2), c);", 0);

        assertThat(result.text()).isEqualTo("outer(inner(a=\"test;x\", b=2), c)");
    }

    @Test
    void reportsUnbalancedParentheses() {
        var reader = new IlimapExpressionReader();

        assertThatThrownBy(() -> reader.readUntilStatementSemicolon(
                "func(a, b(;", 0))
                .isInstanceOf(ReaderException.class)
                .hasMessageContaining("unbalanced");
    }

    @Test
    void reportsUnterminatedStringLiteral() {
        var reader = new IlimapExpressionReader();

        assertThatThrownBy(() -> reader.readUntilStatementSemicolon(
                "x = \"unterminated;", 0))
                .isInstanceOf(ReaderException.class)
                .hasMessageContaining("unterminated string literal");
    }

    @Test
    void handlesEmptyExpression() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(";", 0);

        assertThat(result.text()).isEqualTo("");
    }

    @Test
    void preservesExpressionWithoutSemicolon() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "x + y + z", 0);

        assertThat(result.text()).isEqualTo("x + y + z");
    }

    @Test
    void handlesNestedBraces() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "{a: 1; b: 2};", 0);

        assertThat(result.text()).isEqualTo("{a: 1; b: 2}");
    }

    @Test
    void stopsAtSemicolonOnlyWhenAllNestingClosed() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "f(g(h(\";\")));", 0);

        assertThat(result.text()).isEqualTo("f(g(h(\";\")))");
    }

    @Test
    void handlesStartOffset() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "aaa bbb = p.Name; ccc", 8);

        assertThat(result.text()).isEqualTo("= p.Name");
    }

    @Test
    void tracksRangeCorrectly() {
        var reader = new IlimapExpressionReader();
        var result = reader.readUntilStatementSemicolon(
                "p.Name;", 0);

        assertThat(result.range().start().offset()).isEqualTo(0);
        assertThat(result.range().start().line()).isEqualTo(1);
        assertThat(result.range().start().column()).isEqualTo(1);
    }
}
