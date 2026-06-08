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
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class SurfaceAreaItfIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MODEL = "Ili1SurfaceAreaTestModel";
    private static final String BASKET = "Ili1SurfaceAreaTestModel.TestTopic";

    private static ch.interlis.ili2c.metamodel.TransferDescription transferDescription;

    @BeforeAll
    static void compileModel() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(MODEL, MODELDIR);
        if (result.hasErrors()) {
            fail("ILI1 model compilation errors:\n  " + formatDiagnostics(result));
        }
        transferDescription = result.transferDescription();
    }

    @Test
    void surfaceWithStraightSegmentsWritesValidItfAndReadBackGeometry() throws Exception {
        IomObject surface = surfaceObject("S001", surface(boundary(
                coord(2600000.000, 1200000.000),
                coord(2600100.000, 1200000.000),
                coord(2600100.000, 1200100.000),
                coord(2600000.000, 1200100.000),
                coord(2600000.000, 1200000.000))));

        WriteResult write = writeItf(surface);

        assertThat(write.diagnostics().hasErrors()).isFalse();
        assertThat(write.validation().success())
                .as(write.validation().log())
                .isTrue();

        List<IomObject> objects = readAllObjects(write.path());
        IomObject roundtrip = findFirstBySuffix(objects, ".SurfaceClass");
        assertThat(roundtrip).isNotNull();
        assertThat(roundtrip.getattrvaluecount("Perimeter")).isEqualTo(1);
        assertThat(boundaryCount(roundtrip.getattrobj("Perimeter", 0))).isEqualTo(1);
        assertThat(arcCount(roundtrip.getattrobj("Perimeter", 0))).isZero();
    }

    @Test
    void surfaceWithArcWritesArcpAndReadBackPreservesArc() throws Exception {
        IomObject surface = surfaceObject("SARC", surface(boundary(
                coord(2601000.000, 1201000.000),
                arc(2601050.000, 1201100.000, 2601100.000, 1201000.000),
                coord(2601000.000, 1201000.000))));

        WriteResult write = writeItf(surface);
        String content = Files.readString(write.path());

        assertThat(write.diagnostics().hasErrors()).isFalse();
        assertThat(write.validation().success())
                .as(write.validation().log())
                .isTrue();
        assertThat(content).contains("ARCP 2601050.000 1201100.000");

        IomObject roundtrip = findFirstBySuffix(readAllObjects(write.path()), ".SurfaceClass");
        assertThat(roundtrip).isNotNull();
        assertThat(arcCount(roundtrip.getattrobj("Perimeter", 0))).isEqualTo(1);
    }

    @Test
    void areaWritesHelperTableAndPointOnSurface() throws Exception {
        IomObject area = areaObject("A001", surface(boundary(
                coord(2602000.000, 1202000.000),
                coord(2602100.000, 1202000.000),
                coord(2602100.000, 1202100.000),
                coord(2602000.000, 1202100.000),
                coord(2602000.000, 1202000.000))),
                coord(2602050.000, 1202050.000));

        WriteResult write = writeItf(area);
        String content = Files.readString(write.path());

        assertThat(write.diagnostics().hasErrors()).isFalse();
        assertThat(write.validation().success())
                .as(write.validation().log())
                .isTrue();
        assertThat(content).contains("TABL AreaClass_Geometrie");
        assertThat(content).containsPattern("TABL AreaClass\\ROBJE \\d+ A001 2602050\\.[0-9]+ 1202050\\.[0-9]+");

        IomObject roundtrip = findFirstBySuffix(readAllObjects(write.path()), ".AreaClass");
        assertThat(roundtrip).isNotNull();
        assertThat(roundtrip.getattrvaluecount("Geometrie")).isEqualTo(1);
        assertThat(roundtrip.getattrvaluecount("_itf_Geometrie")).isEqualTo(1);
    }

    @Test
    void surfaceWithHoleSurvivesReadBack() throws Exception {
        IomObject surface = surfaceObject("SHOLE", surface(
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

        WriteResult write = writeItf(surface);

        assertThat(write.diagnostics().hasErrors()).isFalse();
        assertThat(write.validation().success())
                .as(write.validation().log())
                .isTrue();

        IomObject roundtrip = findFirstBySuffix(readAllObjects(write.path()), ".SurfaceClass");
        assertThat(roundtrip).isNotNull();
        assertThat(boundaryCount(roundtrip.getattrobj("Perimeter", 0))).isEqualTo(2);
    }

    private WriteResult writeItf(IomObject... objects) throws Exception {
        Path path = Files.createTempFile("ili1-surface-area-", ".itf");
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxWriter writer = ioFactory.createWriter(path, transferDescription, diagnostics);
        try {
            writer.write(new StartTransferEvent("surface-area-test", null, null));
            writer.write(new StartBasketEvent(BASKET, "b1"));
            for (IomObject object : objects) {
                writer.write(new ch.interlis.iox_j.ObjectEvent(object));
            }
            writer.write(new EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();
        } catch (Exception ex) {
            Files.deleteIfExists(path);
            throw ex;
        }
        ValidationResult validation = IlivalidatorRunner.validate(path, List.of(MODELDIR), MODEL, null);
        return new WriteResult(path, diagnostics, validation);
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

    private static IomObject surfaceObject(String nbIdent, IomObject geometry) {
        Iom_jObject object = new Iom_jObject("Ili1SurfaceAreaTestModel.TestTopic.SurfaceClass", "1");
        object.setattrvalue("NBIdent", nbIdent);
        object.addattrobj("Perimeter", geometry);
        return object;
    }

    private static IomObject areaObject(String nbIdent, IomObject geometry, IomObject pointOnSurface) {
        Iom_jObject object = new Iom_jObject("Ili1SurfaceAreaTestModel.TestTopic.AreaClass", "1");
        object.setattrvalue("NBIdent", nbIdent);
        object.addattrobj("Geometrie", geometry);
        if (pointOnSurface != null) {
            object.addattrobj("_itf_Geometrie", pointOnSurface);
        }
        return object;
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

    private static IomObject arc(double midX, double midY, double endX, double endY) {
        Iom_jObject arc = new Iom_jObject("ARC", null);
        arc.setattrvalue("A1", Double.toString(midX));
        arc.setattrvalue("A2", Double.toString(midY));
        arc.setattrvalue("C1", Double.toString(endX));
        arc.setattrvalue("C2", Double.toString(endY));
        return arc;
    }

    private static int boundaryCount(IomObject geometry) {
        if (geometry == null) {
            return 0;
        }
        if ("MULTISURFACE".equalsIgnoreCase(geometry.getobjecttag())) {
            int count = 0;
            for (int i = 0; i < geometry.getattrvaluecount("surface"); i++) {
                IomObject surface = geometry.getattrobj("surface", i);
                if (surface != null) {
                    count += surface.getattrvaluecount("boundary");
                }
            }
            return count;
        }
        return geometry.getattrvaluecount("boundary");
    }

    private static int arcCount(IomObject geometry) {
        if (geometry == null) {
            return 0;
        }
        int count = 0;
        List<IomObject> polylines = new ArrayList<>();
        if ("MULTISURFACE".equalsIgnoreCase(geometry.getobjecttag())) {
            for (int s = 0; s < geometry.getattrvaluecount("surface"); s++) {
                IomObject surface = geometry.getattrobj("surface", s);
                if (surface == null) {
                    continue;
                }
                for (int b = 0; b < surface.getattrvaluecount("boundary"); b++) {
                    IomObject boundary = surface.getattrobj("boundary", b);
                    if (boundary != null) {
                        for (int p = 0; p < boundary.getattrvaluecount("polyline"); p++) {
                            IomObject polyline = boundary.getattrobj("polyline", p);
                            if (polyline != null) {
                                polylines.add(polyline);
                            }
                        }
                    }
                }
            }
        }
        for (IomObject polyline : polylines) {
            if (polyline == null) {
                continue;
            }
            for (int s = 0; s < polyline.getattrvaluecount("sequence"); s++) {
                IomObject sequence = polyline.getattrobj("sequence", s);
                if (sequence == null) {
                    continue;
                }
                for (int i = 0; i < sequence.getattrvaluecount("segment"); i++) {
                    IomObject segment = sequence.getattrobj("segment", i);
                    if (segment != null && "ARC".equalsIgnoreCase(segment.getobjecttag())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static IomObject findFirstBySuffix(List<IomObject> objects, String suffix) {
        return objects.stream()
                .filter(object -> object.getobjecttag() != null && object.getobjecttag().endsWith(suffix))
                .findFirst()
                .orElse(null);
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((left, right) -> left + "\n  " + right)
                .orElse("<none>");
    }

    private record WriteResult(Path path, DiagnosticCollector diagnostics, ValidationResult validation) {
    }
}
