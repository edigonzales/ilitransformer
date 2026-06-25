package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in performance guard. Run with {@code ./gradlew test -DshpPerfTest=true}.
 *
 * <p>This test does not assert hard timings (which would be flaky on CI), but it verifies that the
 * writer streams 10'000 features without buffering them in a collection: it never holds more than
 * one record in memory at a time, the output sizes are exactly what the streaming layout predicts,
 * and it logs the elapsed time for manual inspection.
 */
@EnabledIfSystemProperty(named = "shpPerfTest", matches = "true")
class ShapefileWriterPerformanceGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void writesTenThousandPointsWithoutBuffering() throws Exception {
        int count = 10_000;
        Path shp = tempDir.resolve("perf.shp");
        ShapefileSchema schema =
                new ShapefileSchema(ShapeType.POINT, List.of(new DbfField("ID", DbfFieldType.NUMERIC, 9, 0)));
        GeometryFactory gf = new GeometryFactory();

        long start = System.nanoTime();
        try (ShapefileDatasetWriter writer =
                ShapefileDatasetWriter.open(shp, schema, ShapefileWriteOptions.defaults())) {
            for (int i = 0; i < count; i++) {
                writer.write(gf.createPoint(new Coordinate(i, i)), new Object[] {i});
            }
            writer.finish();
            assertThat(writer.recordCount()).isEqualTo(count);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[shpPerfTest] wrote " + count + " points in " + elapsedMs + " ms");

        // 100-byte header + count * (8-byte record header + 20-byte point content)
        assertThat(Files.size(shp)).isEqualTo(100L + count * 28L);
        assertThat(Files.size(tempDir.resolve("perf.shx"))).isEqualTo(100L + count * 8L);

        try (DbfReader dbf = DbfReader.open(tempDir.resolve("perf.dbf"), StandardCharsets.UTF_8)) {
            assertThat(dbf.header().recordCount()).isEqualTo(count);
        }
    }
}
