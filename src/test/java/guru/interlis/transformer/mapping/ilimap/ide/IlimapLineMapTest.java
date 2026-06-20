package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import org.junit.jupiter.api.Test;

class IlimapLineMapTest {

    @Test
    void mapsOffsetToZeroBasedPosition() {
        var lineMap = new IlimapLineMap("abc\ndef");

        assertThat(lineMap.offsetToZeroBasedLine(5)).isEqualTo(1);
        assertThat(lineMap.offsetToZeroBasedCharacter(5)).isEqualTo(1);
        assertThat(lineMap.toIdePosition(5)).isEqualTo(new IlimapIdePosition(1, 1));
    }

    @Test
    void mapsInternalOneBasedSourceRangeToZeroBasedIdeRange() {
        var lineMap = new IlimapLineMap("ab\ncd");
        var sourceRange = new IlimapSourceRange(
                new IlimapSourcePosition(3, 2, 1), new IlimapSourcePosition(5, 2, 3));

        assertThat(lineMap.toIdeRange(sourceRange))
                .isEqualTo(new IlimapIdeRange(new IlimapIdePosition(1, 0), new IlimapIdePosition(1, 2)));
    }

    @Test
    void mapsPositionToOffset() {
        var lineMap = new IlimapLineMap("abc\ndef");

        assertThat(lineMap.positionToOffset(1, 2)).isEqualTo(6);
    }

    @Test
    void handlesEmptyDocument() {
        var lineMap = new IlimapLineMap("");

        assertThat(lineMap.lineCount()).isEqualTo(1);
        assertThat(lineMap.offsetToZeroBasedLine(0)).isEqualTo(0);
        assertThat(lineMap.offsetToZeroBasedCharacter(0)).isEqualTo(0);
        assertThat(lineMap.positionToOffset(0, 0)).isEqualTo(0);
        assertThat(lineMap.toIdePosition(0)).isEqualTo(new IlimapIdePosition(0, 0));
    }

    @Test
    void handlesDocumentWithoutFinalNewline() {
        var lineMap = new IlimapLineMap("a\nbc");

        assertThat(lineMap.lineCount()).isEqualTo(2);
        assertThat(lineMap.positionToOffset(1, 2)).isEqualTo(4);
        assertThat(lineMap.toIdePosition(4)).isEqualTo(new IlimapIdePosition(1, 2));
    }

    @Test
    void handlesLastLine() {
        var lineMap = new IlimapLineMap("a\nbc\ndef");

        assertThat(lineMap.lineCount()).isEqualTo(3);
        assertThat(lineMap.toIdePosition(7)).isEqualTo(new IlimapIdePosition(2, 2));
        assertThat(lineMap.positionToOffset(2, 3)).isEqualTo(8);
    }
}
