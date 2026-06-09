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

class PolylineRoundtripTest {

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
    void xtfSurfaceWithStraightPolylineRoundtrip() throws Exception {
        IomObject surface = surfaceObject("SSTRAIGHT", surface(boundary(
                coord(2600000.000, 1200000.000),
                coord(2600100.000, 1200000.000),
                coord(2600100.000, 1200100.000),
                coord(2600000.000, 1200100.000),
                coord(2600000.000, 1200000.000))));

        Path xtfPath = writeXtf(surface);
        try {
            ValidationResult validation = IlivalidatorRunner.validate(xtfPath, List.of(MODELDIR), MODEL, null);
            assertThat(validation.success())
                    .as(validation.log())
                    .isTrue();

            List<IomObject> objects = readAllObjects(xtfPath);
            IomObject roundtrip = findBySuffix(objects, ".SurfaceClass");
            assertThat(roundtrip).isNotNull();
            assertThat(roundtrip.getattrvalue("NBIdent")).isEqualTo("SSTRAIGHT");

            IomObject perimeter = roundtrip.getattrobj("Perimeter", 0);
            assertThat(perimeter).isNotNull();

            int arcCount = countArcs(perimeter);
            assertThat(arcCount).isZero();

            int coordCount = countCoords(perimeter);
            assertThat(coordCount).isEqualTo(5);
        } finally {
            Files.deleteIfExists(xtfPath);
        }
    }

    @Test
    void xtfSurfaceWithArcPolylineRoundtrip() throws Exception {
        IomObject surface = surfaceObject("SARC", surface(boundary(
                coord(2601000.000, 1201000.000),
                arc(2601050.000, 1201100.000, 2601100.000, 1201000.000),
                coord(2601000.000, 1201000.000))));

        Path xtfPath = writeXtf(surface);
        try {
            ValidationResult validation = IlivalidatorRunner.validate(xtfPath, List.of(MODELDIR), MODEL, null);
            assertThat(validation.success())
                    .as(validation.log())
                    .isTrue();

            List<IomObject> objects = readAllObjects(xtfPath);
            IomObject roundtrip = findBySuffix(objects, ".SurfaceClass");
            assertThat(roundtrip).isNotNull();

            IomObject perimeter = roundtrip.getattrobj("Perimeter", 0);
            assertThat(perimeter).isNotNull();

            int arcCount = countArcs(perimeter);
            assertThat(arcCount).isEqualTo(1
            );
        } finally {
            Files.deleteIfExists(xtfPath);
        }
    }

    private Path writeXtf(IomObject... objects) throws Exception {
        Path path = Files.createTempFile("polyline-rt-", ".xtf");
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxWriter writer = ioFactory.createWriter(path, transferDescription);
        writer.write(new StartTransferEvent("polyline-rt", null, null));
        writer.write(new StartBasketEvent(BASKET, "550e8400-e29b-41d4-a716-446655440000"));
        for (IomObject obj : objects) {
            writer.write(new ch.interlis.iox_j.ObjectEvent(obj));
        }
        writer.write(new EndBasketEvent());
        writer.write(new ch.interlis.iox_j.EndTransferEvent());
        writer.flush();
        writer.close();
        return path;
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

    private IomObject surfaceObject(String nbIdent, IomObject geometry) {
        Iom_jObject object = new Iom_jObject(BASKET + ".SurfaceClass", "550e8400-e29b-41d4-a716-446655440001");
        object.setattrvalue("NBIdent", nbIdent);
        object.addattrobj("Perimeter", geometry);
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

    private static int countArcs(IomObject geometry) {
        if (geometry == null) return 0;
        int count = 0;
        if ("MULTISURFACE".equalsIgnoreCase(geometry.getobjecttag())) {
            for (int s = 0; s < geometry.getattrvaluecount("surface"); s++) {
                IomObject surface = geometry.getattrobj("surface", s);
                if (surface != null) {
                    for (int b = 0; b < surface.getattrvaluecount("boundary"); b++) {
                        IomObject boundary = surface.getattrobj("boundary", b);
                        if (boundary != null) {
                            for (int p = 0; p < boundary.getattrvaluecount("polyline"); p++) {
                                IomObject polyline = boundary.getattrobj("polyline", p);
                                if (polyline != null) {
                                    for (int seq = 0; seq < polyline.getattrvaluecount("sequence"); seq++) {
                                        IomObject sequence = polyline.getattrobj("sequence", seq);
                                        if (sequence != null) {
                                            for (int i = 0; i < sequence.getattrvaluecount("segment"); i++) {
                                                IomObject seg = sequence.getattrobj("segment", i);
                                                if (seg != null && "ARC".equalsIgnoreCase(seg.getobjecttag())) {
                                                    count++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    private static int countCoords(IomObject geometry) {
        if (geometry == null) return 0;
        int count = 0;
        if ("MULTISURFACE".equalsIgnoreCase(geometry.getobjecttag())) {
            for (int s = 0; s < geometry.getattrvaluecount("surface"); s++) {
                IomObject surface = geometry.getattrobj("surface", s);
                if (surface != null) {
                    for (int b = 0; b < surface.getattrvaluecount("boundary"); b++) {
                        IomObject boundary = surface.getattrobj("boundary", b);
                        if (boundary != null) {
                            for (int p = 0; p < boundary.getattrvaluecount("polyline"); p++) {
                                IomObject polyline = boundary.getattrobj("polyline", p);
                                if (polyline != null) {
                                    for (int seq = 0; seq < polyline.getattrvaluecount("sequence"); seq++) {
                                        IomObject sequence = polyline.getattrobj("sequence", seq);
                                        if (sequence != null) {
                                            for (int i = 0; i < sequence.getattrvaluecount("segment"); i++) {
                                                IomObject seg = sequence.getattrobj("segment", i);
                                                if (seg != null && "COORD".equalsIgnoreCase(seg.getobjecttag())) {
                                                    count++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    private static IomObject findBySuffix(List<IomObject> objects, String suffix) {
        return objects.stream()
                .filter(obj -> obj.getobjecttag() != null && obj.getobjecttag().endsWith(suffix))
                .findFirst()
                .orElse(null);
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((left, right) -> left + "\n  " + right)
                .orElse("<none>");
    }
}
