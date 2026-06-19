package guru.interlis.transformer.mapping.ilimap.lexer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapLexer.LexerException;

import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapLexerTest {

    @Test
    void tokenizesMappingHeader() {
        var lexer = new IlimapLexer("mapping v2 \"dm01-to-dmav\" {");
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).hasSize(4);
        assertThat(tokens.get(0).isKeyword("mapping")).isTrue();
        assertThat(tokens.get(1).isKeyword("v2")).isTrue();
        assertThat(tokens.get(2).type()).isEqualTo(IlimapTokenType.STRING);
        assertThat(tokens.get(2).text()).isEqualTo("dm01-to-dmav");
        assertThat(tokens.get(3).type()).isEqualTo(IlimapTokenType.LBRACE);
    }

    @Test
    void tokenizesCommentsAndSkipsThem() {
        var lexer = new IlimapLexer("""
                // line comment
                input dm01 /* block comment */ {
                """);
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).hasSize(3);
        assertThat(tokens.get(0).isKeyword("input")).isTrue();
        assertThat(tokens.get(1).type()).isEqualTo(IlimapTokenType.IDENTIFIER);
        assertThat(tokens.get(1).text()).isEqualTo("dm01");
        assertThat(tokens.get(2).type()).isEqualTo(IlimapTokenType.LBRACE);
    }

    @Test
    void lexesLineAndBlockComments() {
        var lexer = new IlimapLexer("""
                // some line comment
                { /* block comment */
                rule
                }
                """);
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).hasSize(3);
        assertThat(tokens.get(0).type()).isEqualTo(IlimapTokenType.LBRACE);
        assertThat(tokens.get(1).isKeyword("rule")).isTrue();
        assertThat(tokens.get(2).type()).isEqualTo(IlimapTokenType.RBRACE);
    }

    @Test
    void hashIsEnumLiteralNotComment() {
        var lexer = new IlimapLexer("#LFP3");
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).type()).isEqualTo(IlimapTokenType.HASH_LITERAL);
        assertThat(tokens.get(0).text()).isEqualTo("#LFP3");
    }

    @Test
    void doesNotTreatHashLiteralAsComment() {
        var lexer = new IlimapLexer("assign { #Cat1 => #Cat2; }");
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens)
                .anyMatch(t ->
                        t.type() == IlimapTokenType.HASH_LITERAL && t.text().equals("#Cat1"));
        assertThat(tokens)
                .anyMatch(t ->
                        t.type() == IlimapTokenType.HASH_LITERAL && t.text().equals("#Cat2"));
    }

    @Test
    void stringEscapesArePreserved() {
        var lexer = new IlimapLexer("\"line1\\nline2\\tindented\\\"quoted\\\"\"");
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).type()).isEqualTo(IlimapTokenType.STRING);
        assertThat(tokens.get(0).text()).isEqualTo("line1\nline2\tindented\"quoted\"");
    }

    @Test
    void lexesStringsWithEscapes() {
        var lexer = new IlimapLexer("\"hello\\\\world\"");
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).type()).isEqualTo(IlimapTokenType.STRING);
        assertThat(tokens.get(0).text()).isEqualTo("hello\\world");
    }

    @Test
    void reportsUnterminatedString() {
        var lexer = new IlimapLexer("\"unterminated");

        assertThatThrownBy(lexer::tokenize)
                .isInstanceOf(LexerException.class)
                .hasMessageContaining("unterminated string literal");
    }

    @Test
    void reportsUnclosedString() {
        var lexer = new IlimapLexer("\"line1\\nstill open");

        assertThatThrownBy(lexer::tokenize)
                .isInstanceOf(LexerException.class)
                .hasMessageContaining("unterminated string literal");
    }

    @Test
    void reportsUnterminatedBlockComment() {
        var lexer = new IlimapLexer("/* start but no end");

        assertThatThrownBy(lexer::tokenize)
                .isInstanceOf(LexerException.class)
                .hasMessageContaining("unterminated block comment");
    }

    @Test
    void reportsUnclosedBlockComment() {
        var lexer = new IlimapLexer("input /* open");

        assertThatThrownBy(lexer::tokenize)
                .isInstanceOf(LexerException.class)
                .hasMessageContaining("unterminated block comment");
    }

    @Test
    void tracksLineAndColumn() {
        var lexer = new IlimapLexer("""
                mapping v2
                input dm01 {
                }
                """);
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens.get(0).range().start().line()).isEqualTo(1);
        assertThat(tokens.get(0).range().start().column()).isEqualTo(1);
    }

    @Test
    void tokenizesAllTokenTypes() {
        var lexer = new IlimapLexer(
                "rule r1 { target dmav => class \"Cls\"; assign { a = b(c, 42, true, false, null); } }");
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).anyMatch(t -> t.isKeyword("rule"));
        assertThat(tokens).anyMatch(t -> t.type() == IlimapTokenType.IDENTIFIER);
        assertThat(tokens).anyMatch(t -> t.type() == IlimapTokenType.STRING);
        assertThat(tokens).anyMatch(t -> t.type() == IlimapTokenType.NUMBER);
        assertThat(tokens)
                .anyMatch(t -> t.type() == IlimapTokenType.BOOLEAN && t.text().equals("true"));
        assertThat(tokens)
                .anyMatch(t -> t.type() == IlimapTokenType.BOOLEAN && t.text().equals("false"));
        assertThat(tokens).anyMatch(t -> t.type() == IlimapTokenType.NULL);
        assertThat(tokens).anyMatch(t -> t.type() == IlimapTokenType.ARROW);
    }

    @Test
    void peekDoesNotConsumeToken() {
        var lexer = new IlimapLexer("mapping v2");
        IlimapToken peeked = lexer.peek();
        assertThat(peeked.isKeyword("mapping")).isTrue();
        assertThat(lexer.next().isKeyword("mapping")).isTrue();
    }

    @Test
    void reservedWordsAreKeywordsNotIdentifiers() {
        var lexer = new IlimapLexer("input output rule assign bag source attribute role");
        List<IlimapToken> tokens = lexer.tokenize();

        assertThat(tokens).allMatch(t -> t.type() == IlimapTokenType.KEYWORD);
    }
}
