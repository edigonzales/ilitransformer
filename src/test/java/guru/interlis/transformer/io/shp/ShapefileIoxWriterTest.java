package guru.interlis.transformer.io.shp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.interlis.iox_j.jts.Jts2iox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Coordinate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileIoxWriterTest {

    private static final String MODEL = "src/test/data/models/shp-writer-test.ili";
    private static final String MODELDIR = "src/test/data/models/";
    private static final String STATION = "ShpWriterTest.Data.Station";
    private static final String TOPIC = "ShpWriterTest.Data";

    private static TransferDescription td;
    private static TypeSystemFacade ts;

    @BeforeAll
    static void compile() {
        IliModelCompileResult result = new IliModelService().compileModel(MODEL, MODELDIR);
        assertThat(result.transferDescription())
                .as("model must compile via ili2c")
                .isNotNull();
        td = result.transferDescription();
        ts = new TypeSystemFacade(td);
    }

    private OutputBinding output(Path shp, Map<String, String> options) {
        return new OutputBinding("out", shp, "ShpWriterTest", "shp", options, td, ts);
    }

    private FormatOpenContext context() {
        return new FormatOpenContext(null, td, new DiagnosticCollector());
    }

    private Iom_jObject station(String oid, String name, String bfs, double x, double y) {
        Iom_jObject o = new Iom_jObject(STATION, oid);
        o.setattrvalue("Name", name);
        if (bfs != null) {
            o.setattrvalue("BfsNr", bfs);
        }
        o.addattrobj("Geometrie", Jts2iox.JTS2coord(new Coordinate(x, y)));
        return o;
    }

    @Test
    void roundtripsPointShapefile(@TempDir Path dir) throws Exception {
        Path shp = dir.resolve("stations.shp");

        IoxWriter writer = ShapefileIoxWriter.open(output(shp, Map.of("class", STATION)), context());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent(TOPIC, "b1"));
        writer.write(new ObjectEvent(station("s1", "Solothurn", "2601", 2600000.0, 1200000.0)));
        writer.write(new ObjectEvent(station("s2", "Bern", "351", 2600100.0, 1200100.0)));
        writer.write(new EndBasketEvent());
        writer.write(new EndTransferEvent());
        writer.flush();
        writer.close();

        assertThat(Files.exists(shp)).isTrue();
        assertThat(Files.exists(dir.resolve("stations.shx"))).isTrue();
        assertThat(Files.exists(dir.resolve("stations.dbf"))).isTrue();
        assertThat(Files.exists(dir.resolve("stations.cpg"))).isTrue();

        List<IomObject> read = readBack(shp);
        assertThat(read).hasSize(2);

        IomObject first = read.get(0);
        assertThat(first.getobjecttag()).isEqualTo(STATION);
        assertThat(first.getattrvalue("Name")).isEqualTo("Solothurn");
        assertThat(first.getattrvalue("BfsNr")).isEqualTo("2601");
        IomObject geom = first.getattrobj("Geometrie", 0);
        assertThat(geom).isNotNull();
        assertThat(Double.parseDouble(geom.getattrvalue("C1"))).isEqualTo(2600000.0);
        assertThat(Double.parseDouble(geom.getattrvalue("C2"))).isEqualTo(1200000.0);
    }

    @Test
    void rejectsObjectOfDifferentClass(@TempDir Path dir) throws Exception {
        Path shp = dir.resolve("mixed.shp");
        IoxWriter writer = ShapefileIoxWriter.open(output(shp, Map.of()), context());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent(TOPIC, "b1"));
        writer.write(new ObjectEvent(station("s1", "A", "1", 2600000.0, 1200000.0)));

        Iom_jObject other = new Iom_jObject("ShpWriterTest.Data.Strecke", "x1");
        assertThatThrownBy(() -> writer.write(new ObjectEvent(other)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("ShpWriterTest.Data.Strecke");
        writer.close();
    }

    @Test
    void rejectsMultipleBasketsByDefault(@TempDir Path dir) throws Exception {
        Path shp = dir.resolve("baskets.shp");
        IoxWriter writer = ShapefileIoxWriter.open(output(shp, Map.of("class", STATION)), context());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent(TOPIC, "b1"));

        assertThatThrownBy(() -> writer.write(new StartBasketEvent(TOPIC, "b2")))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("single basket");
        writer.close();
    }

    @Test
    void rejectsGeometryTypeMismatch(@TempDir Path dir) throws Exception {
        Path shp = dir.resolve("mismatch.shp");
        IoxWriter writer = ShapefileIoxWriter.open(output(shp, Map.of("class", STATION)), context());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent(TOPIC, "b1"));

        Iom_jObject o = new Iom_jObject(STATION, "s1");
        o.setattrvalue("Name", "X");
        // A polygon geometry on a POINT-typed Shapefile must be rejected.
        o.addattrobj("Geometrie", Jts2iox.JTS2surface(squarePolygon()));

        assertThatThrownBy(() -> writer.write(new ObjectEvent(o)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("shape type");
        writer.close();
    }

    @Test
    void emptyTransferWithConfiguredClassWritesEmptyShapefile(@TempDir Path dir) throws Exception {
        Path shp = dir.resolve("empty.shp");
        IoxWriter writer = ShapefileIoxWriter.open(output(shp, Map.of("class", STATION)), context());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent(TOPIC, "b1"));
        writer.write(new EndBasketEvent());
        writer.write(new EndTransferEvent());
        writer.close();

        assertThat(Files.exists(shp)).isTrue();
        assertThat(readBack(shp)).isEmpty();
    }

    @Test
    void roundtripsMultiPointShapefile(@TempDir Path dir) throws Exception {
        String mehrPunkt = "ShpWriterTest.Data.MehrPunkt";
        Path shp = dir.resolve("clusters.shp");

        Iom_jObject o = new Iom_jObject(mehrPunkt, "p1");
        o.setattrvalue("Name", "Cluster");
        o.addattrobj("Lage", Jts2iox.JTS2multicoord(new Coordinate[] {
            new Coordinate(2600000.0, 1200000.0),
            new Coordinate(2600100.0, 1200100.0),
            new Coordinate(2600200.0, 1200050.0)
        }));

        IoxWriter writer = ShapefileIoxWriter.open(output(shp, Map.of("class", mehrPunkt)), context());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent(TOPIC, "b1"));
        writer.write(new ObjectEvent(o));
        writer.write(new EndBasketEvent());
        writer.write(new EndTransferEvent());
        writer.close();

        List<IomObject> read = readBack(shp, mehrPunkt, "Lage");
        assertThat(read).hasSize(1);
        assertThat(read.get(0).getattrvalue("Name")).isEqualTo("Cluster");

        IomObject geom = read.get(0).getattrobj("Lage", 0);
        assertThat(geom).isNotNull();
        com.vividsolutions.jts.geom.Geometry jts = ch.interlis.iox_j.jts.Iox2jts.multicoord2JTS(geom);
        assertThat(jts).isInstanceOf(com.vividsolutions.jts.geom.MultiPoint.class);
        assertThat(jts.getNumGeometries()).isEqualTo(3);
    }

    private List<IomObject> readBack(Path shp) throws Exception {
        return readBack(shp, STATION, "Geometrie");
    }

    private List<IomObject> readBack(Path shp, String className, String geometryAttribute) throws Exception {
        InputBinding binding = new InputBinding(
                "in",
                shp,
                "ShpWriterTest",
                "shp",
                Map.of("class", className, "geometryAttribute", geometryAttribute),
                td,
                ts);
        List<IomObject> objects = new ArrayList<>();
        ShapefileIoxReader reader = ShapefileIoxReader.open(binding, context());
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent oe) {
                    objects.add(oe.getIomObject());
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private static com.vividsolutions.jts.geom.Polygon squarePolygon() {
        com.vividsolutions.jts.geom.GeometryFactory gf = new com.vividsolutions.jts.geom.GeometryFactory();
        return gf.createPolygon(
                gf.createLinearRing(new Coordinate[] {
                    new Coordinate(2600000, 1200000),
                    new Coordinate(2600010, 1200000),
                    new Coordinate(2600010, 1200010),
                    new Coordinate(2600000, 1200010),
                    new Coordinate(2600000, 1200000)
                }),
                null);
    }
}
