package guru.interlis.transformer;

import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("real-data")
class RealDatasetGeometrySmokeTest {

    private static final Path DM01_ITF = Path.of("src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf");
    private static final Path DMAV_XTF = Path.of("src/test/data/DMAV_Version_1_1/DMAVTYM_Alles_V1_1.xtf");
    private static final String DM01_MODEL = "src/test/data/av/models/DM.01-AV-CH_LV95_24d_ili1.ili";
    private static final String DM01_MODELDIR = "src/test/data/av/models/";
    private static final String DMAV_MODEL = "src/test/data/av/models/DMAVTYM_Alles_V1_1.ili";
    private static final String DMAV_MODELDIR = "src/test/data/av/models/";

    @Test
    void readDm01ItfCompletesWithoutIOException() throws Exception {
        assumeTrue(Files.exists(DM01_ITF), "DM01 ITF file not available at " + DM01_ITF);
        assumeTrue(Files.exists(Path.of(DM01_MODEL)), "DM01 model file not available at " + DM01_MODEL);

        IliModelService service = new IliModelService();
        IliModelCompileResult modelResult = service.compileModel(DM01_MODEL, DM01_MODELDIR);
        if (modelResult.hasErrors()) {
            System.out.println("DM01 model compilation issues (may be expected for real model):");
            modelResult.diagnostics().all().forEach(d ->
                    System.out.println("  " + d.severity() + " " + d.code() + ": " + d.message()));
        }
        assumeTrue(modelResult.transferDescription() != null,
                "DM01 model did not produce a TransferDescription");

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        int totalObjects = 0;
        int baskets = 0;
        long startTime = System.currentTimeMillis();

        IoxReader reader = ioFactory.createReader(DM01_ITF, modelResult.transferDescription());
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent) {
                    totalObjects++;
                } else if (event instanceof ch.interlis.iox_j.StartBasketEvent) {
                    baskets++;
                } else if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }

        long elapsedMs = System.currentTimeMillis() - startTime;

        System.out.println("DM01 ITF Smoke Test:");
        System.out.println("  Baskets: " + baskets);
        System.out.println("  Objects: " + totalObjects);
        System.out.println("  Time: " + elapsedMs + "ms");

        assertThat(totalObjects).as("DM01 ITF must contain objects").isPositive();
        assertThat(baskets).as("DM01 ITF must contain baskets").isPositive();
    }

    @Test
    void readDmavXtfCompletesWithoutIOException() throws Exception {
        assumeTrue(Files.exists(DMAV_XTF), "DMAV XTF file not available at " + DMAV_XTF);
        assumeTrue(Files.exists(Path.of(DMAV_MODEL)), "DMAV model file not available at " + DMAV_MODEL);

        IliModelService service = new IliModelService();
        IliModelCompileResult modelResult = service.compileModel(DMAV_MODEL, DMAV_MODELDIR);
        if (modelResult.hasErrors()) {
            System.out.println("DMAV model compilation issues (may be expected for real model):");
            modelResult.diagnostics().all().forEach(d ->
                    System.out.println("  " + d.severity() + " " + d.code() + ": " + d.message()));
        }
        assumeTrue(modelResult.transferDescription() != null,
                "DMAV model did not produce a TransferDescription");

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        int totalObjects = 0;
        int baskets = 0;
        long startTime = System.currentTimeMillis();

        IoxReader reader = ioFactory.createReader(DMAV_XTF, modelResult.transferDescription());
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent) {
                    totalObjects++;
                } else if (event instanceof ch.interlis.iox_j.StartBasketEvent) {
                    baskets++;
                } else if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }

        long elapsedMs = System.currentTimeMillis() - startTime;

        System.out.println("DMAV XTF Smoke Test:");
        System.out.println("  Baskets: " + baskets);
        System.out.println("  Objects: " + totalObjects);
        System.out.println("  Time: " + elapsedMs + "ms");

        assertThat(totalObjects).as("DMAV XTF must contain objects").isPositive();
        assertThat(baskets).as("DMAV XTF must contain baskets").isPositive();
    }
}
