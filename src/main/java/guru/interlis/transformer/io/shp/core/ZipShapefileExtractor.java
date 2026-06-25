package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipShapefileExtractor implements AutoCloseable {

    private final Path tempDir;
    private final ShapefileDataset dataset;

    private ZipShapefileExtractor(Path tempDir, ShapefileDataset dataset) {
        this.tempDir = tempDir;
        this.dataset = dataset;
    }

    public static ZipShapefileExtractor open(Path zipPath, Optional<String> member)
            throws IOException, ShapefileMappingException {
        Path tempDir = Files.createTempDirectory("shpzip");
        List<Path> shpFiles = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                String fileName = lastSep >= 0 ? name.substring(lastSep + 1) : name;

                Path targetFile = tempDir.resolve(fileName);
                Files.createDirectories(targetFile.getParent());
                Files.copy(zis, targetFile);

                if (fileName.toLowerCase().endsWith(".shp")) {
                    shpFiles.add(targetFile);
                }
            }
        }

        if (shpFiles.isEmpty()) {
            deleteRecursively(tempDir);
            throw new ShapefileMappingException(
                    "ZIP input '" + zipPath.getFileName() + "': no .shp file found inside the archive");
        }

        Path selectedShp;
        if (member.isPresent()) {
            String memberName = member.get();
            selectedShp = shpFiles.stream()
                    .filter(p -> p.getFileName().toString().equals(memberName)
                            || p.getFileName().toString().equalsIgnoreCase(memberName))
                    .findFirst()
                    .orElse(null);
            if (selectedShp == null) {
                deleteRecursively(tempDir);
                throw new ShapefileMappingException("ZIP input '" + zipPath.getFileName()
                        + "': member '" + memberName + "' not found in archive. Found .shp files: "
                        + shpFiles.stream().map(p -> p.getFileName().toString()).toList());
            }
        } else if (shpFiles.size() > 1) {
            deleteRecursively(tempDir);
            throw new ShapefileMappingException("ZIP input '" + zipPath.getFileName()
                    + "': multiple .shp files found in archive: "
                    + shpFiles.stream().map(p -> p.getFileName().toString()).toList()
                    + ". Use option 'member' to select one.");
        } else {
            selectedShp = shpFiles.get(0);
        }

        ShapefileDataset dataset = ShapefileDataset.fromPath(selectedShp, false);
        return new ZipShapefileExtractor(tempDir, dataset);
    }

    public ShapefileDataset dataset() {
        return dataset;
    }

    @Override
    public void close() throws IOException {
        deleteRecursively(tempDir);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }
}
