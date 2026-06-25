package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShxWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesIndexReadableByShxReader() throws Exception {
        Path shx = tempDir.resolve("index.shx");

        int numEntries = 3;
        int fileLengthWords = ShapefileHeader.HEADER_SIZE / 2 + numEntries * 4;
        ShapefileHeader header =
                new ShapefileHeader(9994, fileLengthWords, 1000, ShapeType.POINT, 0, 0, 100, 100, 0, 0, 0, 0);

        try (FileChannel channel = openWrite(shx)) {
            ShxWriter writer = new ShxWriter(channel);
            writer.writeHeader(header);
            writer.writeIndexEntry(50, 10);
            writer.writeIndexEntry(64, 10);
            writer.writeIndexEntry(78, 10);
        }

        try (ShxReader reader = ShxReader.open(shx)) {
            assertThat(reader.recordCount()).isEqualTo(3);
            List<ShxReader.ShxIndexEntry> entries = reader.entries();
            assertThat(entries.get(0).offsetWords()).isEqualTo(50);
            assertThat(entries.get(0).contentLengthWords()).isEqualTo(10);
            assertThat(entries.get(1).offsetWords()).isEqualTo(64);
            assertThat(entries.get(2).offsetWords()).isEqualTo(78);
            assertThat(reader.header().shapeType()).isEqualTo(ShapeType.POINT);
        }
    }

    private static FileChannel openWrite(Path path) throws Exception {
        return FileChannel.open(
                path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
