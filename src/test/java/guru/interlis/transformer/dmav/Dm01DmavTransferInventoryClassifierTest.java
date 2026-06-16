package guru.interlis.transformer.dmav;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.interlis.iom.IomObject;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class Dm01DmavTransferInventoryClassifierTest {

    private final Dm01DmavTransferInventoryClassifier classifier = new Dm01DmavTransferInventoryClassifier();

    private record TagEntry(String category, String value) {}

    @Test
    void classifiesLfp3Class() {
        IomObject iom = mock(IomObject.class);
        when(iom.getobjecttag()).thenReturn("DM01AVCH24LV95D.Fixpunkte.LFP3");

        List<TagEntry> captured = new ArrayList<>();
        classifier.classify(iom, (category, value) -> captured.add(new TagEntry(category, value)));

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).category()).isEqualTo("dm01-dmav/LFP3");
        assertThat(captured.get(0).value()).isEqualTo("DM01AVCH24LV95D.Fixpunkte.LFP3");
    }

    @Test
    void classifiesFixpunktClass() {
        IomObject iom = mock(IomObject.class);
        when(iom.getobjecttag()).thenReturn("DM01AVCH24LV95D.Fixpunkte.FIXPUNKT");

        List<TagEntry> captured = new ArrayList<>();
        classifier.classify(iom, (category, value) -> captured.add(new TagEntry(category, value)));

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).category()).isEqualTo("dm01-dmav/LFP3");
    }

    @Test
    void ignoresNonLfp3Class() {
        IomObject iom = mock(IomObject.class);
        when(iom.getobjecttag()).thenReturn("DM01AVCH24LV95D.Liegenschaften.Gebaeude");

        List<TagEntry> captured = new ArrayList<>();
        classifier.classify(iom, (category, value) -> captured.add(new TagEntry(category, value)));

        assertThat(captured).isEmpty();
    }

    @Test
    void ignoresNullTag() {
        IomObject iom = mock(IomObject.class);
        when(iom.getobjecttag()).thenReturn(null);

        List<TagEntry> captured = new ArrayList<>();
        classifier.classify(iom, (category, value) -> captured.add(new TagEntry(category, value)));

        assertThat(captured).isEmpty();
    }

    @Test
    void classifiesUpperCaseLfp3Variant() {
        IomObject iom = mock(IomObject.class);
        when(iom.getobjecttag()).thenReturn("SomeModel.Topic.LFP3_Nachfuehrung");

        List<TagEntry> captured = new ArrayList<>();
        classifier.classify(iom, (category, value) -> captured.add(new TagEntry(category, value)));

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).category()).isEqualTo("dm01-dmav/LFP3");
    }
}
