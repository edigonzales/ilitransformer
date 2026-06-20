package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeRange;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

class IlimapLspRangeMapperTest {

    private final IlimapLspRangeMapper mapper = new IlimapLspRangeMapper();

    @Test
    void mapsZeroBasedIdePositionToLspPosition() {
        assertThat(mapper.toLspPosition(new IlimapIdePosition(2, 4))).isEqualTo(new Position(2, 4));
    }

    @Test
    void mapsZeroBasedIdeRangeToLspRange() {
        var range = new IlimapIdeRange(new IlimapIdePosition(1, 2), new IlimapIdePosition(3, 4));

        assertThat(mapper.toLspRange(range)).isEqualTo(new Range(new Position(1, 2), new Position(3, 4)));
    }
}
