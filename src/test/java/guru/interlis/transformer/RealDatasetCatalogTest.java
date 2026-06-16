package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RealDatasetCatalogTest {

    @Test
    void realDataFilesArePresentAndCataloged() throws Exception {
        List<FileEntry> catalog = new ArrayList<>();

        catalogRealData(Path.of("src/test/data/av/"), catalog);
        catalogRealData(Path.of("src/test/data/DMAV_Version_1_1/"), catalog);

        assertThat(catalog)
                .as("Real data files must be present under src/test/data/")
                .isNotEmpty();

        for (FileEntry entry : catalog) {
            assertThat(entry.path).exists();
            assertThat(entry.size).isGreaterThan(0);
        }
    }

    @Test
    void avModelFilesArePresent() throws Exception {
        Path modelDir = Path.of("src/test/data/av/models/");
        assertThat(modelDir).exists().isDirectory();

        try (Stream<Path> files = Files.list(modelDir)) {
            long count = files.filter(f -> f.toString().endsWith(".ili")).count();
            assertThat(count).as("AV model .ili files must be present").isGreaterThan(10);
        }
    }

    @Test
    void localModelFilesArePresent() throws Exception {
        Path modelDir = Path.of("src/test/data/models/");
        assertThat(modelDir).exists().isDirectory();

        try (Stream<Path> files = Files.list(modelDir)) {
            long count = files.filter(f -> f.toString().endsWith(".ili")).count();
            assertThat(count).as("Local model .ili files must be present").isGreaterThan(5);
        }
    }

    @Test
    void mappingFilesArePresent() throws Exception {
        Path mappingDir = Path.of("src/test/resources/mappings/");
        assertThat(mappingDir).exists().isDirectory();

        try (Stream<Path> files = Files.list(mappingDir)) {
            long count = files.filter(f -> f.toString().endsWith(".yaml")).count();
            assertThat(count).as("Mapping YAML files must be present").isGreaterThan(0);
        }
    }

    private static void catalogRealData(Path dir, List<FileEntry> catalog) throws Exception {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(f -> {
                        String name = f.getFileName().toString().toLowerCase();
                        return name.endsWith(".itf") || name.endsWith(".xtf");
                    })
                    .forEach(f -> {
                        try {
                            catalog.add(new FileEntry(f, f.getFileName().toString(), Files.size(f), fileType(f)));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static String fileType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".itf")) return "ITF";
        if (name.endsWith(".xtf")) return "XTF";
        return "UNKNOWN";
    }

    private record FileEntry(Path path, String name, long size, String type) {}
}
