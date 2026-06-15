package guru.interlis.transformer.model;

import guru.interlis.transformer.testutil.TransferDatasetDescriptor;
import guru.interlis.transformer.testutil.TransferFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TransferInventoryServiceTest {

    private static final Path DM01_ITF = Path.of("src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf")
            .toAbsolutePath();

    private final IliModelService modelService = new IliModelService();

    @Test
    void noOpClassifierProducesEmptyClassifications() {
        TransferInventoryService service = new TransferInventoryService(modelService,
                TransferInventoryClassifier.none());
        TransferInventory inventory = service.inspect(dm01Descriptor());
        assertThat(inventory.classifications()).isEmpty();
    }

    @Test
    void customClassifierPopulatesClassifications() {
        TransferInventoryClassifier tagger = (object, sink) -> {
            String tag = object.getobjecttag();
            if (tag != null) {
                sink.addTag("test-cat", tag);
            }
        };
        TransferInventoryService service = new TransferInventoryService(modelService, tagger);
        TransferInventory inventory = service.inspect(dm01Descriptor());
        assertThat(inventory.classifications()).isNotEmpty();
        assertThat(inventory.classifications()).containsKey("test-cat");
    }

    private static TransferDatasetDescriptor dm01Descriptor() {
        return new TransferDatasetDescriptor(
                "dm01", DM01_ITF, TransferFormat.ITF,
                List.of("DM01AVCH24LV95D"),
                List.of("src/test/data/av/models"),
                -1L);
    }
}
