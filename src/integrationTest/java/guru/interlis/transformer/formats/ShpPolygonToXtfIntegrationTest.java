package guru.interlis.transformer.formats;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import picocli.CommandLine;

class ShpPolygonToXtfIntegrationTest {

    private static final Path EXAMPLE = Path.of("examples/shp-polygon-to-xtf");
    private static final String DEMO_NS = "http://www.interlis.ch/xtf/2.4/DemoPolygonTarget";

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
    void transformsPolygonShapefileToValidXtf(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        createMinimalPolygonShapefile(work.resolve("input/parcels.shp"));
        Path mapping = work.resolve("mapping.yaml");
        Path output = work.resolve("build/out/parcels.xtf");
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
        assertThat(Files.exists(report.resolve("transformation-report.json"))).isTrue();
        assertThat(countObjects(output, "Parcel")).isEqualTo(2);
    }

    @Test
    void transformsPolygonWithHoleToValidXtf(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        createPolygonWithHoleShapefile(work.resolve("input/parcels.shp"));
        Path mapping = work.resolve("mapping.yaml");
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
    }

    @Test
    void rejectsUnclosedPolygonRing(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        createUnclosedRingShapefile(work.resolve("input/parcels.shp"));
        Path mapping = work.resolve("mapping.yaml");

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toString(),
                        "--modeldir",
                        work.resolve("models").toString());

        assertThat(exitCode).isNotZero();
        assertThat(outContent.toString() + errContent.toString()).contains("not closed");
    }

    private record ParcelRec(int id, String label, double[][] allPoints, int[] parts) {}

    private Path prepareExample(Path tempDir) throws Exception {
        Path work = tempDir.resolve("shp-polygon-to-xtf");
        copyRecursively(EXAMPLE, work);

        Path input = work.resolve("input/parcels.shp").toAbsolutePath();
        Path output = work.resolve("build/out/parcels.xtf").toAbsolutePath();
        Path models = work.resolve("models").toAbsolutePath();

        Path m = work.resolve("mapping.yaml");
        String content = Files.readString(m)
                .replace("\"input/parcels.shp\"", quoted(m, input))
                .replace("\"build/out/parcels.xtf\"", quoted(m, output))
                .replace("\"models\"", quoted(m, models));
        Files.writeString(m, content);
        return work;
    }

    private void createMinimalPolygonShapefile(Path shpPath) throws Exception {
        Files.createDirectories(shpPath.getParent());
        writePolygonShapefile(
                shpPath,
                new ParcelRec(
                        1,
                        "P1",
                        new double[][] {{0.0, 0.0}, {0.0, 10.0}, {10.0, 10.0}, {10.0, 0.0}, {0.0, 0.0}},
                        new int[] {0}),
                new ParcelRec(
                        2,
                        "P2",
                        new double[][] {{15.0, 5.0}, {15.0, 15.0}, {25.0, 15.0}, {25.0, 5.0}, {15.0, 5.0}},
                        new int[] {0}));
    }

    private void createPolygonWithHoleShapefile(Path shpPath) throws Exception {
        Files.createDirectories(shpPath.getParent());
        writePolygonShapefile(
                shpPath,
                new ParcelRec(
                        1,
                        "Parcel",
                        new double[][] {
                            {0.0, 0.0}, {0.0, 20.0}, {30.0, 20.0}, {30.0, 0.0}, {0.0, 0.0},
                            {10.0, 5.0}, {20.0, 5.0}, {20.0, 15.0}, {10.0, 15.0}, {10.0, 5.0}
                        },
                        new int[] {0, 5}));
    }

    private void createUnclosedRingShapefile(Path shpPath) throws Exception {
        Files.createDirectories(shpPath.getParent());
        writePolygonShapefile(
                shpPath,
                new ParcelRec(
                        1, "Bad", new double[][] {{0.0, 0.0}, {10.0, 0.0}, {10.0, 10.0}, {0.0, 9.0}}, new int[] {0}));
    }

    private void writePolygonShapefile(Path shpPath, ParcelRec... parcels) throws Exception {
        String baseName = shpPath.getFileName().toString();
        String nameWithoutExt = baseName.substring(0, baseName.lastIndexOf('.'));
        Path dir = shpPath.getParent();
        Path dbfPath = dir.resolve(nameWithoutExt + ".dbf");

        int numRecords = parcels.length;

        int[] contentWords = new int[numRecords];
        int totalContentBytes = 0;
        for (int i = 0; i < numRecords; i++) {
            int partsCount = parcels[i].parts.length;
            int pointsCount = parcels[i].allPoints.length;
            int recordBytes = 4 + 32 + 4 + 4 + partsCount * 4 + pointsCount * 16;
            totalContentBytes += recordBytes;
            contentWords[i] = recordBytes / 2;
        }

        int shpContentLength = 100 + numRecords * 8 + totalContentBytes;

        ByteBuffer shpBuf = ByteBuffer.allocate(shpContentLength);
        shpBuf.order(ByteOrder.BIG_ENDIAN);
        shpBuf.putInt(9994);
        shpBuf.putInt(0);
        shpBuf.putInt(0);
        shpBuf.putInt(0);
        shpBuf.putInt(0);
        shpBuf.putInt(0);
        shpBuf.putInt(shpContentLength / 2);
        shpBuf.order(ByteOrder.LITTLE_ENDIAN);
        shpBuf.putInt(1000);
        shpBuf.putInt(5);
        double minX = 0, minY = 0, maxX = 0, maxY = 0;
        if (numRecords > 0) {
            minX = parcels[0].allPoints[0][0];
            minY = parcels[0].allPoints[0][1];
            maxX = minX;
            maxY = minY;
            for (ParcelRec p : parcels) {
                for (double[] pt : p.allPoints) {
                    minX = Math.min(minX, pt[0]);
                    minY = Math.min(minY, pt[1]);
                    maxX = Math.max(maxX, pt[0]);
                    maxY = Math.max(maxY, pt[1]);
                }
            }
        }
        shpBuf.putDouble(minX);
        shpBuf.putDouble(minY);
        shpBuf.putDouble(maxX);
        shpBuf.putDouble(maxY);
        shpBuf.putDouble(0.0);
        shpBuf.putDouble(0.0);
        shpBuf.putDouble(0.0);
        shpBuf.putDouble(0.0);

        for (int i = 0; i < numRecords; i++) {
            ParcelRec p = parcels[i];
            int partsCount = p.parts.length;
            int pointsCount = p.allPoints.length;

            double recMinX = p.allPoints[0][0], recMinY = p.allPoints[0][1];
            double recMaxX = recMinX, recMaxY = recMinY;
            for (double[] pt : p.allPoints) {
                recMinX = Math.min(recMinX, pt[0]);
                recMinY = Math.min(recMinY, pt[1]);
                recMaxX = Math.max(recMaxX, pt[0]);
                recMaxY = Math.max(recMaxY, pt[1]);
            }

            shpBuf.order(ByteOrder.BIG_ENDIAN);
            shpBuf.putInt(i + 1);
            shpBuf.putInt(contentWords[i]);
            shpBuf.order(ByteOrder.LITTLE_ENDIAN);
            shpBuf.putInt(5);
            shpBuf.putDouble(recMinX);
            shpBuf.putDouble(recMinY);
            shpBuf.putDouble(recMaxX);
            shpBuf.putDouble(recMaxY);
            shpBuf.putInt(partsCount);
            shpBuf.putInt(pointsCount);
            for (int part : p.parts) {
                shpBuf.putInt(part);
            }
            for (double[] pt : p.allPoints) {
                shpBuf.putDouble(pt[0]);
                shpBuf.putDouble(pt[1]);
            }
        }

        shpBuf.flip();
        byte[] shpData = new byte[shpBuf.remaining()];
        shpBuf.get(shpData);
        Files.write(shpPath, shpData);

        byte[] dbfData = createParcelDbf(parcels);
        Files.write(dbfPath, dbfData);
    }

    private byte[] createParcelDbf(ParcelRec... parcels) {
        int recordCount = parcels.length;
        int fieldCount = 2;
        int fieldDescrBytes = fieldCount * 32 + 1;
        int headerLen = 32 + fieldDescrBytes;
        int idLen = 10;
        int labelLen = 80;
        int recordLen = 1 + idLen + labelLen;

        ByteBuffer buf = ByteBuffer.allocate(headerLen + recordCount * recordLen);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x03);
        buf.put((byte) 125);
        buf.put((byte) 6);
        buf.put((byte) 25);
        buf.putInt(recordCount);
        buf.putShort((short) headerLen);
        buf.putShort((short) recordLen);
        for (int i = 0; i < 20; i++) buf.put((byte) 0);

        byte[] idName = new byte[11];
        System.arraycopy("ID".getBytes(StandardCharsets.US_ASCII), 0, idName, 0, 2);
        buf.put(idName);
        buf.put((byte) 'N');
        for (int i = 0; i < 4; i++) buf.put((byte) 0);
        buf.put((byte) idLen);
        buf.put((byte) 0);
        for (int i = 0; i < 14; i++) buf.put((byte) 0);

        byte[] labelField = new byte[11];
        System.arraycopy("LABEL".getBytes(StandardCharsets.US_ASCII), 0, labelField, 0, 5);
        buf.put(labelField);
        buf.put((byte) 'C');
        for (int i = 0; i < 4; i++) buf.put((byte) 0);
        buf.put((byte) labelLen);
        buf.put((byte) 0);
        for (int i = 0; i < 14; i++) buf.put((byte) 0);

        buf.put((byte) 0x0D);

        for (ParcelRec p : parcels) {
            buf.put((byte) 0x20);

            byte[] idBytes = new byte[idLen];
            byte[] rawId = String.valueOf(p.id).getBytes(StandardCharsets.UTF_8);
            System.arraycopy(rawId, 0, idBytes, 0, Math.min(rawId.length, idLen));
            for (int j = rawId.length; j < idLen; j++) idBytes[j] = (byte) ' ';
            buf.put(idBytes);

            byte[] lBytes = new byte[labelLen];
            byte[] rawLabel = p.label.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(rawLabel, 0, lBytes, 0, Math.min(rawLabel.length, labelLen));
            for (int j = rawLabel.length; j < labelLen; j++) lBytes[j] = (byte) ' ';
            buf.put(lBytes);
        }

        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    private static String quoted(Path mappingPath, Path path) {
        String text = path.toString();
        if (mappingPath.getFileName().toString().endsWith(".ilimap")) {
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
