package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.geom.ShpGeometryDecoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileDatasetWriterTest {

    @TempDir
    Path tempDir;

    private final GeometryFactory gf = new GeometryFactory();
    private final ShpGeometryDecoder decoder = new ShpGeometryDecoder();

    private final List<DbfField> nameField = List.of(new DbfField("NAME", DbfFieldType.CHARACTER, 10, 0));

    @Test
    void writesPointDatasetWithCorrectSidecarsAndOffsets() throws Exception {
        Path shp = tempDir.resolve("points.shp");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.POINT, nameField);

        try (ShapefileDatasetWriter writer =
                ShapefileDatasetWriter.open(shp, schema, ShapefileWriteOptions.defaults())) {
            writer.write(gf.createPoint(new Coordinate(1, 1)), new Object[] {"A"});
            writer.write(gf.createPoint(new Coordinate(2, 3)), new Object[] {"B"});
            writer.write(gf.createPoint(new Coordinate(5, 4)), new Object[] {"C"});
            writer.finish();
        }

        assertThat(Files.exists(shp)).isTrue();
        assertThat(Files.exists(tempDir.resolve("points.shx"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("points.dbf"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("points.cpg"))).isEqualTo("UTF-8");

        try (ShpReader reader = ShpReader.open(shp)) {
            assertThat(reader.header().shapeType()).isEqualTo(ShapeType.POINT);
            // 100 byte header + 3 * (8 + 20) bytes = 184 bytes = 92 words
            assertThat(reader.header().fileLengthWords()).isEqualTo(92);
            assertThat(reader.header().xmin()).isEqualTo(1.0);
            assertThat(reader.header().ymin()).isEqualTo(1.0);
            assertThat(reader.header().xmax()).isEqualTo(5.0);
            assertThat(reader.header().ymax()).isEqualTo(4.0);

            List<Geometry> geoms = readAll(reader);
            assertThat(geoms).hasSize(3);
            assertThat(geoms.get(1).getCoordinate()).isEqualTo(new Coordinate(2, 3));
        }

        try (ShxReader shx = ShxReader.open(tempDir.resolve("points.shx"))) {
            assertThat(shx.recordCount()).isEqualTo(3);
            assertThat(shx.entries().get(0).offsetWords()).isEqualTo(50);
            assertThat(shx.entries().get(0).contentLengthWords()).isEqualTo(10);
            assertThat(shx.entries().get(1).offsetWords()).isEqualTo(64);
            assertThat(shx.entries().get(2).offsetWords()).isEqualTo(78);
        }

        try (DbfReader dbf = DbfReader.open(tempDir.resolve("points.dbf"), StandardCharsets.UTF_8)) {
            assertThat(dbf.header().recordCount()).isEqualTo(3);
            assertThat(dbf.readNext().orElseThrow().values().get(0).trim()).isEqualTo("A");
            assertThat(dbf.readNext().orElseThrow().values().get(0).trim()).isEqualTo("B");
            assertThat(dbf.readNext().orElseThrow().values().get(0).trim()).isEqualTo("C");
        }
    }

    @Test
    void writesPolylineDatasetRoundtrips() throws Exception {
        Path shp = tempDir.resolve("lines.shp");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.POLYLINE, nameField);

        Geometry line = gf.createLineString(
                new Coordinate[] {new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(10, 10)});

        try (ShapefileDatasetWriter writer =
                ShapefileDatasetWriter.open(shp, schema, ShapefileWriteOptions.defaults())) {
            writer.write(line, new Object[] {"L1"});
            writer.finish();
        }

        try (ShpReader reader = ShpReader.open(shp)) {
            List<Geometry> geoms = readAll(reader);
            assertThat(geoms).hasSize(1);
            assertThat(geoms.get(0).equalsTopo(line)).isTrue();
        }
    }

    @Test
    void writesPolygonDatasetRoundtrips() throws Exception {
        Path shp = tempDir.resolve("polys.shp");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.POLYGON, nameField);

        LinearRing shell = gf.createLinearRing(new Coordinate[] {
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });
        LinearRing hole = gf.createLinearRing(new Coordinate[] {
            new Coordinate(3, 3), new Coordinate(3, 6), new Coordinate(6, 6), new Coordinate(6, 3), new Coordinate(3, 3)
        });
        Polygon polygon = gf.createPolygon(shell, new LinearRing[] {hole});

        try (ShapefileDatasetWriter writer =
                ShapefileDatasetWriter.open(shp, schema, ShapefileWriteOptions.defaults())) {
            writer.write(polygon, new Object[] {"P1"});
            writer.finish();
        }

        try (ShpReader reader = ShpReader.open(shp)) {
            List<Geometry> geoms = readAll(reader);
            assertThat(geoms).hasSize(1);
            assertThat(geoms.get(0).getGeometryType()).isEqualTo("Polygon");
            assertThat(((Polygon) geoms.get(0)).getNumInteriorRing()).isEqualTo(1);
            assertThat(geoms.get(0).getArea()).isEqualTo(91.0);
        }
    }

    @Test
    void writesEmptyDataset() throws Exception {
        Path shp = tempDir.resolve("empty.shp");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.POINT, nameField);

        try (ShapefileDatasetWriter writer =
                ShapefileDatasetWriter.open(shp, schema, ShapefileWriteOptions.defaults())) {
            writer.finish();
        }

        try (ShpReader reader = ShpReader.open(shp)) {
            assertThat(reader.header().fileLengthWords()).isEqualTo(50);
            assertThat(reader.readNext()).isEmpty();
        }
        try (DbfReader dbf = DbfReader.open(tempDir.resolve("empty.dbf"), StandardCharsets.UTF_8)) {
            assertThat(dbf.header().recordCount()).isEqualTo(0);
        }
    }

    @Test
    void writesPrjWhenConfigured() throws Exception {
        Path shp = tempDir.resolve("withprj.shp");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.POINT, nameField);
        ShapefileWriteOptions options = new ShapefileWriteOptions(
                StandardCharsets.UTF_8,
                Optional.of("PROJCS[\"CH1903+\"]"),
                ShapefileWriteOptions.OverflowPolicy.STRICT);

        try (ShapefileDatasetWriter writer = ShapefileDatasetWriter.open(shp, schema, options)) {
            writer.write(gf.createPoint(new Coordinate(1, 1)), new Object[] {"A"});
            writer.finish();
        }

        assertThat(Files.readString(tempDir.resolve("withprj.prj"))).isEqualTo("PROJCS[\"CH1903+\"]");
    }

    @Test
    void closeWithoutFinishLeavesNoFiles() throws Exception {
        Path shp = tempDir.resolve("aborted.shp");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.POINT, nameField);

        try (ShapefileDatasetWriter writer =
                ShapefileDatasetWriter.open(shp, schema, ShapefileWriteOptions.defaults())) {
            writer.write(gf.createPoint(new Coordinate(1, 1)), new Object[] {"A"});
        }

        assertThat(Files.exists(shp)).isFalse();
        assertThat(Files.exists(tempDir.resolve("aborted.shx"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("aborted.dbf"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("aborted.shp.tmp"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("aborted.shx.tmp"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("aborted.dbf.tmp"))).isFalse();
    }

    @Test
    void rejectsUnsupportedShapeTypeOnOpen() {
        Path shp = tempDir.resolve("bad.shp");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.MULTIPOINT, nameField);

        assertThatThrownBy(() -> ShapefileDatasetWriter.open(shp, schema, ShapefileWriteOptions.defaults()))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("MULTIPOINT");
    }

    @Test
    void rejectsNonShpTargetPath() {
        Path target = tempDir.resolve("notashape.txt");
        ShapefileSchema schema = new ShapefileSchema(ShapeType.POINT, nameField);

        assertThatThrownBy(() -> ShapefileDatasetWriter.open(target, schema, ShapefileWriteOptions.defaults()))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining(".shp");
    }

    private List<Geometry> readAll(ShpReader reader) throws Exception {
        List<Geometry> geoms = new ArrayList<>();
        Optional<ShapeRecord> record;
        while ((record = reader.readNext()).isPresent()) {
            geoms.add(decoder.decode(record.get()));
        }
        return geoms;
    }
}
