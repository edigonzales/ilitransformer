package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import guru.interlis.transformer.app.IlivalidatorRunner;
import guru.interlis.transformer.app.IlivalidatorRunner.ValidationResult;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class CoordRoundtripTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MODEL = "DmavGeomTestModel";
    private static final String BASKET = "DmavGeomTestModel.Fixpunkte";

    private static ch.interlis.ili2c.metamodel.TransferDescription transferDescription;

    @BeforeAll
    static void compileModel() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(MODEL, MODELDIR);
        if (result.hasErrors()) {
            fail("Model compilation errors:\n  " + formatDiagnostics(result));
        }
        transferDescription = result.transferDescription();
    }

    @Test
    void xtfCoordWriteValidateReadBack() throws Exception {
        IomObject lfp3 = new Iom_jObject(BASKET + ".LFP3",
                "550e8400-e29b-41d4-a716-446655440000");
        lfp3.setattrvalue("NBIdent", "LFP001");
        lfp3.setattrvalue("Nummer", "001");
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", "2600000.0");
        coord.setattrvalue("C2", "1200000.0");
        lfp3.addattrobj("Geometrie", coord);
        lfp3.setattrvalue("Lagegenauigkeit", "5.0");

        Path xtfPath = Files.createTempFile("coord-rt-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(xtfPath, transferDescription);
            writer.write(new StartTransferEvent("coord-rt-test", null, null));
            writer.write(new StartBasketEvent(BASKET, "550e8400-e29b-41d4-a716-446655440001"));
            writer.write(new ch.interlis.iox_j.ObjectEvent(lfp3));
            writer.write(new EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();

            ValidationResult validation = IlivalidatorRunner.validate(xtfPath, List.of(MODELDIR), MODEL, null);
            assertThat(validation.success())
                    .as(validation.log())
                    .isTrue();

            List<IomObject> objects = readAllObjects(xtfPath);
            assertThat(objects).hasSize(1);
            IomObject roundtrip = objects.get(0);
            assertThat(roundtrip.getattrvalue("NBIdent")).isEqualTo("LFP001");
            assertThat(roundtrip.getattrvaluecount("Geometrie")).isEqualTo(1);
            IomObject roundtripCoord = roundtrip.getattrobj("Geometrie", 0);
            assertThat(roundtripCoord.getattrvalue("C1")).isEqualTo("2600000.0");
            assertThat(roundtripCoord.getattrvalue("C2")).isEqualTo("1200000.0");
        } finally {
            Files.deleteIfExists(xtfPath);
        }
    }

    @Test
    void itfCoordWriteAndReadBack() throws Exception {
        IomObject lfp3 = new Iom_jObject(BASKET + ".LFP3", "2");
        lfp3.setattrvalue("NBIdent", "LFP002");
        lfp3.setattrvalue("Nummer", "002");
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", "2600500.0");
        coord.setattrvalue("C2", "1200500.0");
        lfp3.addattrobj("Geometrie", coord);
        lfp3.setattrvalue("Lagegenauigkeit", "3.0");

        Path itfPath = Files.createTempFile("coord-rt-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            DiagnosticCollector diagnostics = new DiagnosticCollector();
            IoxWriter writer = ioFactory.createWriter(itfPath, transferDescription, diagnostics);
            writer.write(new StartTransferEvent("coord-rt-test", null, null));
            writer.write(new StartBasketEvent(BASKET, "b2"));
            writer.write(new ch.interlis.iox_j.ObjectEvent(lfp3));
            writer.write(new EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();

            assertThat(diagnostics.hasErrors())
                    .as(diagnostics.all().toString())
                    .isFalse();

            List<IomObject> objects = readAllObjects(itfPath);
            assertThat(objects).hasSize(1);
            IomObject roundtrip = objects.get(0);
            assertThat(roundtrip.getattrvalue("NBIdent")).isEqualTo("LFP002");
            assertThat(roundtrip.getattrvaluecount("Geometrie")).isEqualTo(1);
        } finally {
            Files.deleteIfExists(itfPath);
        }
    }

    private List<IomObject> readAllObjects(Path path) throws Exception {
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        List<IomObject> objects = new ArrayList<>();
        IoxReader reader = ioFactory.createReader(path, transferDescription);
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    objects.add(objectEvent.getIomObject());
                } else if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((left, right) -> left + "\n  " + right)
                .orElse("<none>");
    }
}
