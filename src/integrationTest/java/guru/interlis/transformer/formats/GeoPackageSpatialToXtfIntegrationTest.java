package guru.interlis.transformer.formats;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.WKBWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import picocli.CommandLine;

/**
 * Phase 5 end-to-end guard: a spatial GeoPackage table with point geometry must be
 * transformed into a valid INTERLIS 2.4 XTF transfer through both the YAML and the
 * {@code .ilimap} pipeline, including {@code --validate}.
 *
 * <p>The input spatial GeoPackage is created programmatically before each test using
 * SQLite JDBC with JTS-encoded WKB point geometry. No binary fixture is committed.
 */
class GeoPackageSpatialToXtfIntegrationTest {

    private static final Path EXAMPLE = Path.of("examples/gpkg-spatial-to-xtf");
    private static final String DEMO_NS = "http://www.interlis.ch/xtf/2.4/DemoSpatialTarget";
    private static final int LV95_SRID = 2056;

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), LV95_SRID);
    private static final WKBWriter WKB_WRITER = new WKBWriter(2, false);

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUpStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void transformsGeoPackagePointGeometryToValidXtfUsingYamlMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.yaml");
    }

    @Test
    void transformsGeoPackagePointGeometryToValidXtfUsingIlimapMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.ilimap");
    }

    private void runAndAssertValid(Path tempDir, String mappingName) throws Exception {
        Path work = prepareExample(tempDir);
        Path gpkg = createSpatialGeoPackage(work.resolve("input/stations.gpkg"));
        Path mapping = work.resolve(mappingName);
        Path output = work.resolve("build/out/stations.xtf");
        Path report = work.resolve("build/report");

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toString(),
                        "--modeldir",
                        work.resolve("models").toString(),
                        "--validate",
                        "--report",
                        report.toString());

        assertThat(exitCode)
                .as("stdout:%n%s%nstderr:%n%s", outContent, errContent)
                .isZero();
        assertThat(Files.exists(output)).as("output XTF created").isTrue();
        assertThat(Files.exists(report.resolve("transformation-report.json")))
                .as("report written")
                .isTrue();
        assertThat(countObjects(output, "Station")).isEqualTo(2);
    }

    private Path prepareExample(Path tempDir) throws Exception {
        Path work = tempDir.resolve("gpkg-spatial-to-xtf");
        copyRecursively(EXAMPLE, work);

        Path input = work.resolve("input/stations.gpkg").toAbsolutePath();
        Path output = work.resolve("build/out/stations.xtf").toAbsolutePath();
        Path models = work.resolve("models").toAbsolutePath();

        for (String name : List.of("mapping.yaml", "mapping.ilimap")) {
            Path m = work.resolve(name);
            String content = Files.readString(m)
                    .replace("\"input/stations.gpkg\"", quoted(name, input))
                    .replace("\"build/out/stations.xtf\"", quoted(name, output))
                    .replace("\"models\"", quoted(name, models));
            Files.writeString(m, content);
        }
        return work;
    }

    private Path createSpatialGeoPackage(Path target) throws Exception {
        Files.createDirectories(target.getParent());
        String url = "jdbc:sqlite:" + target.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA application_id=1196437808");

            stmt.execute("CREATE TABLE gpkg_spatial_ref_sys ("
                    + " srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY,"
                    + " organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL,"
                    + " definition TEXT NOT NULL, description TEXT)");

            stmt.execute(
                    "INSERT INTO gpkg_spatial_ref_sys (srs_name, srs_id, organization, organization_coordsys_id, definition)"
                            + " VALUES ('Undefined', 0, 'NONE', 0, 'Undefined')");

            stmt.execute(
                    "INSERT INTO gpkg_spatial_ref_sys (srs_name, srs_id, organization, organization_coordsys_id, definition)"
                            + " VALUES ('CH1903+ / LV95', 2056, 'EPSG', 2056, 'PROJCS[\"CH1903+ / LV95\","
                            + "GEOGCS[\"CH1903+\",DATUM[\"CH1903+\",SPHEROID[\"Bessel 1841\",6377397.155,299.1528128]],"
                            + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]],"
                            + "PROJECTION[\"Hotine_Oblique_Mercator_Azimuth_Center\"],"
                            + "UNIT[\"metre\",1],AUTHORITY[\"EPSG\",\"2056\"]]')");

            stmt.execute("CREATE TABLE gpkg_contents ("
                    + " table_name TEXT NOT NULL PRIMARY KEY,"
                    + " data_type TEXT NOT NULL,"
                    + " identifier TEXT,"
                    + " description TEXT DEFAULT '',"
                    + " last_change DATETIME NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),"
                    + " min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE,"
                    + " srs_id INTEGER)");

            stmt.execute("CREATE TABLE gpkg_geometry_columns ("
                    + " table_name TEXT NOT NULL, column_name TEXT NOT NULL,"
                    + " geometry_type_name TEXT NOT NULL, srs_id INTEGER NOT NULL,"
                    + " z TINYINT NOT NULL, m TINYINT NOT NULL,"
                    + " PRIMARY KEY (table_name, column_name))");

            stmt.execute("CREATE TABLE stations (" + " identifier TEXT, name TEXT, geom BLOB)");

            stmt.execute(
                    "INSERT INTO gpkg_contents(table_name, data_type, identifier, srs_id, min_x, min_y, max_x, max_y)"
                            + " VALUES ('stations', 'features', 'stations', 2056,"
                            + " 2607000.0, 1228000.0, 2636000.0, 1243000.0)");

            stmt.execute("INSERT INTO gpkg_geometry_columns(table_name, column_name, geometry_type_name, srs_id, z, m)"
                    + " VALUES ('stations', 'geom', 'POINT', 2056, 0, 0)");

            byte[] gpb1 = toGpb(2607600.0, 1228500.0);
            byte[] gpb2 = toGpb(2635000.0, 1242000.0);

            try (PreparedStatement ps =
                    conn.prepareStatement("INSERT INTO stations(identifier, name, geom) VALUES (?, ?, ?)")) {
                ps.setString(1, "G1");
                ps.setString(2, "Solothurn");
                ps.setBytes(3, gpb1);
                ps.executeUpdate();

                ps.setString(1, "G2");
                ps.setString(2, "Olten");
                ps.setBytes(3, gpb2);
                ps.executeUpdate();
            }
        }
        return target;
    }

    private static byte[] toGpb(double x, double y) {
        Point point = GF.createPoint(new Coordinate(x, y));
        byte[] rawWkb = WKB_WRITER.write(point);

        byte[] gpb = new byte[8 + rawWkb.length];

        gpb[0] = 0x47;
        gpb[1] = 0x50;
        gpb[2] = 0;
        gpb[3] = 0;

        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(gpb, 4, 4);
        buf.order(java.nio.ByteOrder.BIG_ENDIAN);
        buf.putInt(LV95_SRID);

        System.arraycopy(rawWkb, 0, gpb, 8, rawWkb.length);

        return gpb;
    }

    private static String quoted(String mappingName, Path path) {
        String text = path.toString();
        if (mappingName.endsWith(".ilimap")) {
            text = text.replace("\\", "\\\\");
        }
        return "\"" + text + "\"";
    }

    private void copyRecursively(Path source, Path target) throws Exception {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            }
        }
    }

    private int countObjects(Path xtf, String localName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document document = factory.newDocumentBuilder().parse(xtf.toFile());
        return document.getElementsByTagNameNS(DEMO_NS, localName).getLength();
    }
}
