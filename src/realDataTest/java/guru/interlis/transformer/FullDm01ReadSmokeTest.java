package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;

import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("real-data")
public class FullDm01ReadSmokeTest {

    private static final Path DM01_ITF = Path.of("src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf");
    private static final String DM01_MODEL = "src/test/data/av/models/DM.01-AV-CH_LV95_24d_ili1.ili";
    private static final String DM01_MODELDIR = "src/test/data/av/models/";

    @Test
    void readFullDm01Dataset() throws Exception {
        assumeTrue(Files.exists(DM01_ITF), "DM01 ITF not available");
        assumeTrue(Files.exists(Path.of(DM01_MODEL)), "DM01 model not available");

        IliModelService service = new IliModelService();
        IliModelCompileResult modelResult = service.compileModel(DM01_MODEL, DM01_MODELDIR);
        assumeTrue(modelResult.transferDescription() != null, "DM01 model not compilable");

        long start = System.currentTimeMillis();
        Map<String, Long> objectsByClass = new LinkedHashMap<>();
        Set<String> basketIds = new HashSet<>();
        long totalObjects = 0;

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxReader reader = ioFactory.createReader(DM01_ITF, modelResult.transferDescription());

        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof StartBasketEvent basket) {
                    if (basket.getBid() != null) basketIds.add(basket.getBid());
                } else if (event instanceof ObjectEvent obj) {
                    totalObjects++;
                    String tag = obj.getIomObject().getobjecttag();
                    objectsByClass.merge(tag, 1L, Long::sum);
                } else if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("=== DM01 Dataset Smoke Report ===");
        System.out.println("Total objects: " + totalObjects);
        System.out.println("Baskets: " + basketIds.size());
        System.out.println("Classes: " + objectsByClass.size());
        System.out.println("Elapsed: " + elapsed + " ms");
        System.out.println();
        System.out.println("Top 20 classes by object count:");
        objectsByClass.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));

        assertThat(totalObjects).as("DM01 dataset must contain objects").isPositive();
        assertThat(elapsed).as("Reading should complete within 5 minutes").isLessThan(300_000L);
    }
}
