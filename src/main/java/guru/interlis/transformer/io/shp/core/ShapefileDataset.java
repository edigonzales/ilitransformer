package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record ShapefileDataset(Path shp, Path shx, Path dbf, Optional<Path> prj, Optional<Path> cpg) {

    public static ShapefileDataset fromPath(Path shpPath, boolean requireShx)
            throws ShapefileMappingException, IOException {
        if (!Files.isRegularFile(shpPath)) {
            throw new ShapefileMappingException("SHP file not found: " + shpPath);
        }

        Path dir = shpPath.getParent() != null ? shpPath.getParent() : Path.of(".");
        String baseName = baseName(shpPath);

        Path dbf = findSidecar(dir, baseName, ".dbf", true, shpPath);
        Path shx = findSidecar(dir, baseName, ".shx", requireShx, shpPath);
        Optional<Path> prj = Optional.ofNullable(findSidecar(dir, baseName, ".prj", false, shpPath));
        Optional<Path> cpg = Optional.ofNullable(findSidecar(dir, baseName, ".cpg", false, shpPath));

        return new ShapefileDataset(shpPath, shx, dbf, prj, cpg);
    }

    public String baseName() {
        return baseName(shp);
    }

    private static Path findSidecar(Path dir, String baseName, String extension, boolean required, Path shp)
            throws ShapefileMappingException {
        Path exact = dir.resolve(baseName + extension);
        if (Files.isRegularFile(exact)) {
            return exact;
        }

        String lowerExt = extension.toLowerCase();
        String lowerBase = baseName.toLowerCase();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> matches = stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String fn = p.getFileName().toString().toLowerCase();
                        return fn.equals(lowerBase + lowerExt);
                    })
                    .toList();
            if (matches.size() == 1) {
                return matches.get(0);
            }
            if (matches.size() > 1) {
                throw new ShapefileMappingException("SHP input '" + shp.getFileName() + "': ambiguous " + extension
                        + " sidecar: found multiple matching files in " + dir);
            }
        } catch (IOException e) {
            throw new ShapefileMappingException(
                    "SHP input '" + shp.getFileName() + "': cannot list directory " + dir + ": " + e.getMessage(), e);
        }

        if (required) {
            throw new ShapefileMappingException("SHP input '" + shp.getFileName()
                    + "': missing required sidecar file '" + baseName + extension + "' next to '" + shp.getFileName()
                    + "'.");
        }
        return null;
    }

    private static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
