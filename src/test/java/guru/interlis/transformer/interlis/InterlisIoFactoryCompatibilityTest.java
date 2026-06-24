package guru.interlis.transformer.interlis;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.geometry.ItfGeometryWriter;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartTransferEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Characterization tests that pin the current observable behavior of {@link InterlisIoFactory}.
 *
 * <p>These tests are the safety net for the planned I/O format refactoring. They assert the
 * factory's extension-based dispatch (reader and writer), its rejection of unsupported extensions,
 * and the {@code EndTransferAwareReader} contract: a reader yields exactly one {@code
 * EndTransferEvent} and then returns {@code null} on every subsequent read.
 *
 * <p>XTF/XML behavior is characterized via a self-contained write-then-read round trip using a
 * checked-in test model (compiled by name, mirroring {@code XtfReadOwnOutputTest}). The ITF case
 * reuses the checked-in, validator-proven {@code ili1-surface-area} fixture. No new format behavior
 * is introduced here.
 */
class InterlisIoFactoryCompatibilityTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String XTF_MODEL = "DmavSurfaceAreaTestModel";
    private static final String XTF_BASKET = "DmavSurfaceAreaTestModel.TestTopic";
    private static final String XTF_CLASS = XTF_BASKET + ".SurfaceClass";

    private static final String ITF_MODEL = "Ili1SurfaceAreaTestModel";
    private static final Path ITF_FIXTURE = Path.of("src/test/resources/transfers/ili1-surface-area/input.itf");

    private static TransferDescription xtfTd;
    private static TransferDescription itfTd;

    private final InterlisIoFactory factory = new InterlisIoFactory();

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        xtfTd = compile(service, XTF_MODEL);
        itfTd = compile(service, ITF_MODEL);
    }

    private static TransferDescription compile(IliModelService service, String model) {
        IliModelCompileResult result = service.compileModel(model, MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors for %s: %s", model, result.diagnostics())
                .isFalse();
        return result.transferDescription();
    }

    // -- Reader dispatch + EndTransferAwareReader behavior -------------------

    @Test
    void createsXtfReaderForXtfFile(@TempDir Path tempDir) throws Exception {
        Path xtf = tempDir.resolve("transfer.xtf");
        writeSampleTransfer(xtf);
        assertReaderConsumesTransfer(xtf, xtfTd);
    }

    @Test
    void createsXtfReaderForXmlFile(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("transfer.xml");
        writeSampleTransfer(xml);
        assertReaderConsumesTransfer(xml, xtfTd);
    }

    @Test
    void createsItfReaderForItfFile() throws Exception {
        assertReaderConsumesTransfer(ITF_FIXTURE, itfTd);
    }

    @Test
    void rejectsUnsupportedInputExtension() {
        assertThatThrownBy(() -> factory.createReader(Path.of("source.csv"), xtfTd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported input file type");
    }

    // -- Writer dispatch -----------------------------------------------------

    @Test
    void createsXtfWriterForXtfFile(@TempDir Path tempDir) throws Exception {
        IoxWriter writer = factory.createWriter(tempDir.resolve("out.xtf"), xtfTd);
        try {
            assertThat(writer).isInstanceOf(XtfWriter.class);
        } finally {
            writer.close();
        }
    }

    @Test
    void createsItfWriterForItfFile(@TempDir Path tempDir) throws Exception {
        IoxWriter writer = factory.createWriter(tempDir.resolve("out.itf"), itfTd);
        try {
            assertThat(writer).isInstanceOf(ItfGeometryWriter.class);
        } finally {
            writer.close();
        }
    }

    @Test
    void rejectsUnsupportedOutputExtension(@TempDir Path tempDir) {
        assertThatThrownBy(() -> factory.createWriter(tempDir.resolve("out.dat"), xtfTd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported output file type");
    }

    // -- Helpers -------------------------------------------------------------

    private void writeSampleTransfer(Path path) throws Exception {
        IomObject object = new Iom_jObject(XTF_CLASS, "o1");
        object.setattrvalue("NBIdent", "RT001");

        IoxWriter writer = factory.createWriter(path, xtfTd);
        try {
            writer.write(new ch.interlis.iox_j.StartTransferEvent("compat-test", null, null));
            writer.write(new ch.interlis.iox_j.StartBasketEvent(XTF_BASKET, "b1"));
            writer.write(new ch.interlis.iox_j.ObjectEvent(object));
            writer.write(new ch.interlis.iox_j.EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
        } finally {
            writer.close();
        }
    }

    private void assertReaderConsumesTransfer(Path path, TransferDescription td) throws Exception {
        IoxReader reader = factory.createReader(path, td);
        assertThat(reader).isNotNull();
        try {
            List<IoxEvent> events = new ArrayList<>();
            IoxEvent event;
            while ((event = reader.read()) != null) {
                events.add(event);
            }
            assertThat(events).isNotEmpty();
            assertThat(events.get(0)).isInstanceOf(StartTransferEvent.class);
            assertThat(events).anyMatch(e -> e instanceof ObjectEvent);
            assertThat(events).filteredOn(e -> e instanceof EndTransferEvent).hasSize(1);
            // EndTransferAwareReader keeps returning null on every read after EndTransferEvent.
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }
}
