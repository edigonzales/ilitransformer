package guru.interlis.transformer.io.shp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Enforces the hard architecture rule from the Shapefile spec: the {@code shp} package and its
 * sub-packages must never depend on GeoTools or the OGC GeoAPI. The streaming Shapefile core is a
 * deliberate, GeoTools-free reimplementation.
 */
class NoGeoToolsImportTest {

    private static final Path SHP_SOURCE_ROOT =
            Path.of("src", "main", "java", "guru", "interlis", "transformer", "io", "shp");

    private static final List<String> FORBIDDEN = List.of("org.geotools", "org.opengis");

    @Test
    void shapefileSourcesDoNotImportGeoTools() throws IOException {
        assertThat(Files.isDirectory(SHP_SOURCE_ROOT))
                .as("Shapefile source root must exist: " + SHP_SOURCE_ROOT.toAbsolutePath())
                .isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SHP_SOURCE_ROOT)) {
            List<Path> javaFiles =
                    paths.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    for (String forbidden : FORBIDDEN) {
                        if (line.contains(forbidden)) {
                            violations.add(file + ":" + (i + 1) + " -> " + line.trim());
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("Shapefile sources must not reference GeoTools/GeoAPI packages")
                .isEmpty();
    }
}
