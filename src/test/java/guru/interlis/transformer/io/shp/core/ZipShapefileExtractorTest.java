package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipShapefileExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsSingleShpFromZip() throws Exception {
        Path zip = createZipWithSingleShp();

        try (ZipShapefileExtractor extractor = ZipShapefileExtractor.open(zip, Optional.empty())) {
            ShapefileDataset ds = extractor.dataset();
            assertThat(ds.shp().getFileName().toString()).isEqualTo("data.shp");
            assertThat(ds.dbf().getFileName().toString()).isEqualTo("data.dbf");
            assertThat(ds.shp()).exists();
            assertThat(ds.dbf()).exists();
        }
    }

    @Test
    void zipTempDirIsCleanedUpOnClose() throws Exception {
        Path zip = createZipWithSingleShp();
        Path tempDirTracked;

        try (ZipShapefileExtractor extractor = ZipShapefileExtractor.open(zip, Optional.empty())) {
            tempDirTracked = extractor.dataset().shp().getParent();
            assertThat(tempDirTracked).exists();
        }

        assertThat(tempDirTracked).doesNotExist();
    }

    @Test
    void selectsMemberByExactNameOption() throws Exception {
        Path zip = createZipWithMultipleShp();

        try (ZipShapefileExtractor extractor = ZipShapefileExtractor.open(zip, Optional.of("b.shp"))) {
            assertThat(extractor.dataset().shp().getFileName().toString()).isEqualTo("b.shp");
        }
    }

    @Test
    void selectsMemberByCaseInsensitiveName() throws Exception {
        Path zip = createZipWithMultipleShp();

        try (ZipShapefileExtractor extractor = ZipShapefileExtractor.open(zip, Optional.of("B.SHP"))) {
            assertThat(extractor.dataset().shp().getFileName().toString()).isEqualTo("b.shp");
        }
    }

    @Test
    void rejectsMissingMember() throws Exception {
        Path zip = createZipWithMultipleShp();

        assertThatThrownBy(() -> {
                    try (ZipShapefileExtractor ignored =
                            ZipShapefileExtractor.open(zip, Optional.of("nonexistent.shp"))) {}
                })
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("member")
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsMultipleShpWithoutMember() throws Exception {
        Path zip = createZipWithMultipleShp();

        assertThatThrownBy(() -> {
                    try (ZipShapefileExtractor ignored = ZipShapefileExtractor.open(zip, Optional.empty())) {}
                })
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("multiple .shp files");
    }

    @Test
    void rejectsZipWithNoShp() throws Exception {
        Path zip = createZipWithoutShp();

        assertThatThrownBy(() -> {
                    try (ZipShapefileExtractor ignored = ZipShapefileExtractor.open(zip, Optional.empty())) {}
                })
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("no .shp file");
    }

    @Test
    void extractsSidecarsAlongWithShp() throws Exception {
        Path zip = createZipWithSingleShp();

        try (ZipShapefileExtractor extractor = ZipShapefileExtractor.open(zip, Optional.empty())) {
            ShapefileDataset ds = extractor.dataset();
            assertThat(ds.shp()).exists();
            assertThat(ds.dbf()).exists();
        }
    }

    private Path createZipWithSingleShp() throws Exception {
        return createZip(tempDir.resolve("single.zip"), new String[][] {{"data.shp", "data.dbf"}});
    }

    private Path createZipWithMultipleShp() throws Exception {
        return createZip(tempDir.resolve("multi.zip"), new String[][] {
            {"a.shp", "a.dbf"},
            {"b.shp", "b.dbf"}
        });
    }

    private Path createZipWithoutShp() throws Exception {
        return createZip(tempDir.resolve("noshp.zip"), new String[][] {{null, "data.dbf"}});
    }

    private Path createZip(Path zipPath, String[][] files) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (String[] filePair : files) {
                String shpName = filePair[0];
                String dbfName = filePair[1];

                if (shpName != null) {
                    byte[] shpData = createMinimalShp();
                    zos.putNextEntry(new java.util.zip.ZipEntry(shpName));
                    zos.write(shpData);
                    zos.closeEntry();
                }

                if (dbfName != null) {
                    byte[] dbfData = createMinimalDbf();
                    zos.putNextEntry(new java.util.zip.ZipEntry(dbfName));
                    zos.write(dbfData);
                    zos.closeEntry();
                }
            }
        }

        Files.write(zipPath, baos.toByteArray());
        return zipPath;
    }

    private static byte[] createMinimalShp() {
        ByteBuffer buf = ByteBuffer.allocate(100);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(9994);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(50);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1000);
        buf.putInt(ShapeType.POINT.code());
        for (int i = 0; i < 8; i++) buf.putDouble(0.0);
        return buf.array();
    }

    private static byte[] createMinimalDbf() {
        int headerLength = 32 + 1;
        int recordLength = 1;
        ByteBuffer buf = ByteBuffer.allocate(headerLength);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x03);
        buf.put((byte) 125);
        buf.put((byte) 6);
        buf.put((byte) 25);
        buf.putInt(0);
        buf.putShort((short) headerLength);
        buf.putShort((short) recordLength);
        for (int i = 0; i < 20; i++) buf.put((byte) 0);
        buf.put((byte) 0x0D);
        return buf.array();
    }
}
