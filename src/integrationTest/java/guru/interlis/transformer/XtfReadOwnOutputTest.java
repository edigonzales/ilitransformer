package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import guru.interlis.transformer.app.IlivalidatorRunner;
import guru.interlis.transformer.app.IlivalidatorRunner.ValidationResult;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class XtfReadOwnOutputTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MODEL = "DmavSurfaceAreaTestModel";
    private static final String BASKET = "DmavSurfaceAreaTestModel.TestTopic";

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
    void ownXtfOutputCanBeReadBackWithModelContext() throws Exception {
        IomObject surfaceObj = new Iom_jObject(BASKET + ".SurfaceClass", "550e8400-e29b-41d4-a716-446655440000");
        surfaceObj.setattrvalue("NBIdent", "RT001");
        IomObject geom = surface(boundary(
                coord(2600000.000, 1200000.000),
                coord(2600100.000, 1200000.000),
                coord(2600100.000, 1200100.000),
                coord(2600000.000, 1200100.000),
                coord(2600000.000, 1200000.000)));
        surfaceObj.addattrobj("Perimeter", geom);

        Path xtfPath = Files.createTempFile("own-output-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(xtfPath, transferDescription);
            writer.write(new StartTransferEvent("own-output-test", null, null));
            writer.write(new StartBasketEvent(BASKET, "550e8400-e29b-41d4-a716-446655440001"));
            writer.write(new ch.interlis.iox_j.ObjectEvent(surfaceObj));
            writer.write(new EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();

            ValidationResult validation = IlivalidatorRunner.validate(xtfPath, List.of(MODELDIR), MODEL, null);
            assertThat(validation.success()).as(validation.log()).isTrue();

            IoxReader reader = ioFactory.createReader(xtfPath, transferDescription);
            List<IomObject> readBack = new ArrayList<>();
            try {
                IoxEvent event;
                while ((event = reader.read()) != null) {
                    if (event instanceof ObjectEvent obj) {
                        readBack.add(obj.getIomObject());
                    } else if (event instanceof EndTransferEvent) {
                        break;
                    }
                }
            } finally {
                reader.close();
            }

            assertThat(readBack).isNotEmpty();
            IomObject readSurface = readBack.stream()
                    .filter(obj ->
                            obj.getobjecttag() != null && obj.getobjecttag().endsWith(".SurfaceClass"))
                    .findFirst()
                    .orElse(null);
            assertThat(readSurface).isNotNull();
            assertThat(readSurface.getattrvalue("NBIdent")).isEqualTo("RT001");
            assertThat(readSurface.getattrvaluecount("Perimeter")).isEqualTo(1);
        } finally {
            Files.deleteIfExists(xtfPath);
        }
    }

    @Test
    void ownXtfWithHolesCanBeReadBack() throws Exception {
        IomObject surfaceObj = new Iom_jObject(BASKET + ".SurfaceClass", "550e8400-e29b-41d4-a716-446655440002");
        surfaceObj.setattrvalue("NBIdent", "HOLE01");
        surfaceObj.addattrobj(
                "Perimeter",
                surface(
                        boundary(
                                coord(2603000.000, 1203000.000),
                                coord(2603200.000, 1203000.000),
                                coord(2603200.000, 1203200.000),
                                coord(2603000.000, 1203200.000),
                                coord(2603000.000, 1203000.000)),
                        boundary(
                                coord(2603050.000, 1203050.000),
                                coord(2603150.000, 1203050.000),
                                coord(2603150.000, 1203150.000),
                                coord(2603050.000, 1203150.000),
                                coord(2603050.000, 1203050.000))));

        Path xtfPath = Files.createTempFile("own-output-hole-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(xtfPath, transferDescription);
            writer.write(new StartTransferEvent("own-output-hole", null, null));
            writer.write(new StartBasketEvent(BASKET, "550e8400-e29b-41d4-a716-446655440003"));
            writer.write(new ch.interlis.iox_j.ObjectEvent(surfaceObj));
            writer.write(new EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();

            ValidationResult validation = IlivalidatorRunner.validate(xtfPath, List.of(MODELDIR), MODEL, null);
            assertThat(validation.success()).as(validation.log()).isTrue();

            IoxReader reader = ioFactory.createReader(xtfPath, transferDescription);
            List<IomObject> readBack = new ArrayList<>();
            try {
                IoxEvent event;
                while ((event = reader.read()) != null) {
                    if (event instanceof ObjectEvent obj) {
                        readBack.add(obj.getIomObject());
                    } else if (event instanceof EndTransferEvent) {
                        break;
                    }
                }
            } finally {
                reader.close();
            }

            assertThat(readBack).isNotEmpty();
            IomObject readSurface = readBack.stream()
                    .filter(obj ->
                            obj.getobjecttag() != null && obj.getobjecttag().endsWith(".SurfaceClass"))
                    .findFirst()
                    .orElse(null);
            assertThat(readSurface).isNotNull();
            assertThat(readSurface.getattrvalue("NBIdent")).isEqualTo("HOLE01");
        } finally {
            Files.deleteIfExists(xtfPath);
        }
    }

    private static IomObject surface(IomObject... boundaries) {
        Iom_jObject multiSurface = new Iom_jObject("MULTISURFACE", null);
        Iom_jObject surface = new Iom_jObject("SURFACE", null);
        for (IomObject boundary : boundaries) {
            surface.addattrobj("boundary", boundary);
        }
        multiSurface.addattrobj("surface", surface);
        return multiSurface;
    }

    private static IomObject boundary(IomObject... segments) {
        Iom_jObject boundary = new Iom_jObject("boundary", null);
        boundary.addattrobj("polyline", polyline(segments));
        return boundary;
    }

    private static IomObject polyline(IomObject... segments) {
        Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
        Iom_jObject sequence = new Iom_jObject("sequence", null);
        for (IomObject segment : segments) {
            sequence.addattrobj("segment", segment);
        }
        polyline.addattrobj("sequence", sequence);
        return polyline;
    }

    private static IomObject coord(double x, double y) {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", Double.toString(x));
        coord.setattrvalue("C2", Double.toString(y));
        return coord;
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((left, right) -> left + "\n  " + right)
                .orElse("<none>");
    }
}
