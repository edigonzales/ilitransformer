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
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import picocli.CommandLine;

class ShpPointToXtfIntegrationTest {

    private static final Path EXAMPLE = Path.of("examples/shp-to-xtf");
    private static final String DEMO_NS = "http://www.interlis.ch/xtf/2.4/DemoTarget";

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
    void transformsPointShapefileToValidXtfUsingYamlMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.yaml");
    }

    @Test
    void transformsPointShapefileToValidXtfUsingIlimapMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.ilimap");
    }

    @Test
    void rejectsInvalidClassOption(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        Path shp = createMinimalPointShapefile(work.resolve("input/stations.shp"));
        Path mapping = work.resolve("mapping.yaml");
        String content = Files.readString(mapping).replace("DemoShpSource.Data.Station", "NonExistent.Model.Class");
        Files.writeString(mapping, content);

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toString(),
                        "--modeldir",
                        work.resolve("models").toString());

        assertThat(exitCode).isNotZero();
    }

    @Test
    void rejectsMissingDbf(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        Path shp = createMinimalPointShapefile(work.resolve("input/stations.shp"));
        Files.delete(work.resolve("input/stations.dbf"));

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        work.resolve("mapping.yaml").toString(),
                        "--modeldir",
                        work.resolve("models").toString());

        assertThat(exitCode).isNotZero();
        assertThat(outContent.toString() + errContent.toString()).contains("dbf");
    }

    private void runAndAssertValid(Path tempDir, String mappingName) throws Exception {
        Path work = prepareExample(tempDir);
        Path shp = createMinimalPointShapefile(work.resolve("input/stations.shp"));
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
        Path work = tempDir.resolve("shp-to-xtf");
        copyRecursively(EXAMPLE, work);

        Path input = work.resolve("input/stations.shp").toAbsolutePath();
        Path output = work.resolve("build/out/stations.xtf").toAbsolutePath();
        Path models = work.resolve("models").toAbsolutePath();

        for (String name : List.of("mapping.yaml", "mapping.ilimap")) {
            Path m = work.resolve(name);
            String content = Files.readString(m)
                    .replace("\"input/stations.shp\"", quoted(name, input))
                    .replace("\"build/out/stations.xtf\"", quoted(name, output))
                    .replace("\"models\"", quoted(name, models));
            Files.writeString(m, content);
        }
        return work;
    }

    private Path createMinimalPointShapefile(Path shpPath) throws Exception {
        Files.createDirectories(shpPath.getParent());

        writePointShapefile(
                shpPath,
                new PointStation(2601, "Solothurn", 7.542, 47.208),
                new PointStation(2610, "Olten", 7.906, 47.351));

        return shpPath;
    }

    private record PointStation(int bfsnr, String name, double x, double y) {}

    private void writePointShapefile(Path shpPath, PointStation... stations) throws Exception {
        String baseName = shpPath.getFileName().toString();
        String nameWithoutExt = baseName.substring(0, baseName.lastIndexOf('.'));
        Path dir = shpPath.getParent();

        Path dbfPath = dir.resolve(nameWithoutExt + ".dbf");

        int numRecords = stations.length;
        int contentLengthWords = 10;
        int shpContentLength = 100 + numRecords * (8 + contentLengthWords * 2);

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
        shpBuf.putInt(1);
        shpBuf.putDouble(stations.length > 0 ? minX(stations) : 0.0);
        shpBuf.putDouble(stations.length > 0 ? minY(stations) : 0.0);
        shpBuf.putDouble(stations.length > 0 ? maxX(stations) : 0.0);
        shpBuf.putDouble(stations.length > 0 ? maxY(stations) : 0.0);
        shpBuf.putDouble(0.0);
        shpBuf.putDouble(0.0);
        shpBuf.putDouble(0.0);
        shpBuf.putDouble(0.0);

        for (int i = 0; i < numRecords; i++) {
            PointStation st = stations[i];
            shpBuf.order(ByteOrder.BIG_ENDIAN);
            shpBuf.putInt(i + 1);
            shpBuf.putInt(contentLengthWords);
            shpBuf.order(ByteOrder.LITTLE_ENDIAN);
            shpBuf.putInt(1);
            shpBuf.putDouble(st.x);
            shpBuf.putDouble(st.y);
        }

        shpBuf.flip();
        byte[] shpData = new byte[shpBuf.remaining()];
        shpBuf.get(shpData);
        Files.write(shpPath, shpData);

        byte[] dbfData = createDbf(stations);
        Files.write(dbfPath, dbfData);
    }

    private static double minX(PointStation[] stations) {
        double d = stations[0].x;
        for (PointStation s : stations) d = Math.min(d, s.x);
        return d;
    }

    private static double minY(PointStation[] stations) {
        double d = stations[0].y;
        for (PointStation s : stations) d = Math.min(d, s.y);
        return d;
    }

    private static double maxX(PointStation[] stations) {
        double d = stations[0].x;
        for (PointStation s : stations) d = Math.max(d, s.x);
        return d;
    }

    private static double maxY(PointStation[] stations) {
        double d = stations[0].y;
        for (PointStation s : stations) d = Math.max(d, s.y);
        return d;
    }

    private byte[] createDbf(PointStation... stations) {
        int recordCount = stations.length;
        int fieldCount = 2;

        int fieldDescrBytes = fieldCount * 32 + 1;
        int headerLen = 32 + fieldDescrBytes;
        int recordLen = 1;
        int bfsnrLen = 10;
        int nameLen = 80;
        recordLen += bfsnrLen + nameLen;

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

        byte[] bfsName = new byte[11];
        byte[] bfsNameBytes = "BFS_NR".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bfsNameBytes, 0, bfsName, 0, bfsNameBytes.length);
        buf.put(bfsName);
        buf.put((byte) 'N');
        for (int i = 0; i < 4; i++) buf.put((byte) 0);
        buf.put((byte) bfsnrLen);
        buf.put((byte) 0);
        for (int i = 0; i < 14; i++) buf.put((byte) 0);

        byte[] nameField = new byte[11];
        byte[] nameFieldBytes = "NAME".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameFieldBytes, 0, nameField, 0, nameFieldBytes.length);
        buf.put(nameField);
        buf.put((byte) 'C');
        for (int i = 0; i < 4; i++) buf.put((byte) 0);
        buf.put((byte) nameLen);
        buf.put((byte) 0);
        for (int i = 0; i < 14; i++) buf.put((byte) 0);

        buf.put((byte) 0x0D);

        for (PointStation st : stations) {
            buf.put((byte) 0x20);

            String bfsStr = String.valueOf(st.bfsnr);
            byte[] bfsBytes = new byte[bfsnrLen];
            byte[] rawBfs = bfsStr.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(rawBfs, 0, bfsBytes, 0, Math.min(rawBfs.length, bfsnrLen));
            for (int j = rawBfs.length; j < bfsnrLen; j++) bfsBytes[j] = (byte) ' ';
            buf.put(bfsBytes);

            byte[] nameBytes = new byte[nameLen];
            byte[] rawName = st.name.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(rawName, 0, nameBytes, 0, Math.min(rawName.length, nameLen));
            for (int j = rawName.length; j < nameLen; j++) nameBytes[j] = (byte) ' ';
            buf.put(nameBytes);
        }

        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
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
