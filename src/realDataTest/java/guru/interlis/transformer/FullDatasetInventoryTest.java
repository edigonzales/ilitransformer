package guru.interlis.transformer;

import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TransferInventory;
import guru.interlis.transformer.model.TransferInventoryService;
import guru.interlis.transformer.testutil.RealDatasetCatalog;
import guru.interlis.transformer.testutil.TransferDatasetDescriptor;
import guru.interlis.transformer.testutil.TransferFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Tag("real-data")
class FullDatasetInventoryTest {

    private static final Path DATA_DIR = Path.of("src/test/data/DMAV_Version_1_1");
    private static final String MODEL_DIR = "src/test/data/av/models";

    private static IliModelService modelService;
    private static TransferInventoryService inventoryService;

    @BeforeAll
    static void setUp() {
        modelService = new IliModelService();
        inventoryService = new TransferInventoryService(modelService);
    }

    @Test
    void dm01DatasetInventory() {
        Path dm01Path = findTransferFile(DATA_DIR, ".itf");
        assertThat(dm01Path).exists();

        TransferDatasetDescriptor dm01Desc = new TransferDatasetDescriptor(
                dm01Path.getFileName().toString(), dm01Path.toAbsolutePath(),
                TransferFormat.ITF,
                List.of("DM01AVCH24LV95D"), List.of(MODEL_DIR),
                dm01Path.toFile().length());

        TransferInventory inventory = inventoryService.inspect(dm01Desc);

        assertThat(inventory.totalObjects()).isGreaterThan(0);
        assertThat(inventory.basketCount()).isGreaterThan(0);

        System.out.println("=== DM01 Dataset Inventory ===");
        System.out.println("File: " + inventory.transferFile());
        System.out.println("Format: " + inventory.format());
        System.out.println("Models: " + inventory.modelNames());
        System.out.println("Total objects: " + inventory.totalObjects());
        System.out.println("Baskets: " + inventory.basketCount());
        System.out.println("Classes (top 20 by count):");
        inventory.classStats().stream()
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .limit(20)
                .forEach(cs -> System.out.printf("  %s: %d%n", cs.className(), cs.count()));
        System.out.println("LFP3-related: " + inventory.lfp3RelatedClasses());

        long lfp3Count = inventory.classStats().stream()
                .filter(cs -> cs.className().toUpperCase().contains("LFP3"))
                .mapToLong(TransferInventory.ClassStats::count)
                .sum();
        assertThat(lfp3Count).as("Must have at least 2 LFP3 objects").isGreaterThanOrEqualTo(2);
    }

    @Test
    void dmavDatasetInventory() throws Exception {
        Path dmavPath = findTransferFile(DATA_DIR, ".xtf");
        assertThat(dmavPath).exists();

        String modelDirs = MODEL_DIR + ";https://models.interlis.ch";
        TransferDatasetDescriptor dmavDesc = new TransferDatasetDescriptor(
                dmavPath.getFileName().toString(), dmavPath.toAbsolutePath(),
                TransferFormat.XTF,
                List.of("DMAVTYM_Alles_V1_1"),
                List.of(MODEL_DIR, "https://models.interlis.ch"),
                dmavPath.toFile().length());

        TransferInventory inventory = inventoryService.inspect(dmavDesc);

        assertThat(inventory.totalObjects()).isGreaterThan(0);
        assertThat(inventory.basketCount()).isGreaterThan(0);

        System.out.println("=== DMAV Dataset Inventory ===");
        System.out.println("File: " + inventory.transferFile());
        System.out.println("Format: " + inventory.format());
        System.out.println("Models: " + inventory.modelNames());
        System.out.println("Total objects: " + inventory.totalObjects());
        System.out.println("Baskets: " + inventory.basketCount());
        System.out.println("Classes (top 20 by count):");
        inventory.classStats().stream()
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .limit(20)
                .forEach(cs -> System.out.printf("  %s: %d%n", cs.className(), cs.count()));
        System.out.println("LFP3-related: " + inventory.lfp3RelatedClasses());

        long lfp3Count = inventory.classStats().stream()
                .filter(cs -> cs.className().toUpperCase().contains("LFP3"))
                .mapToLong(TransferInventory.ClassStats::count)
                .sum();
        assertThat(lfp3Count).as("Must have at least 2 LFP3 objects").isGreaterThanOrEqualTo(2);
    }

    private static Path findTransferFile(Path dir, String extension) {
        try (var files = Files.walk(dir)) {
            return files
                    .filter(f -> f.getFileName().toString().toLowerCase().endsWith(extension))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No " + extension + " file found under " + dir));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
