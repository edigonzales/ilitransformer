package guru.interlis.transformer.app;

import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.TransferFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TransactionalOutputManager implements AutoCloseable {

    private final Path tempDir;
    private final Map<String, Path> tempPathsByOutputId = new LinkedHashMap<>();
    private final Map<String, OutputBinding> bindingsById = new LinkedHashMap<>();
    private final boolean keepTemporaryFiles;

    public TransactionalOutputManager(boolean keepTemporaryFiles) {
        this.keepTemporaryFiles = keepTemporaryFiles;
        try {
            this.tempDir = Files.createTempDirectory("ili-transformer-");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }

    public Path createTemporaryOutput(OutputBinding binding) {
        String outputId = binding.outputId();
        Path targetPath = binding.path();
        String extension = extension(binding.format());
        String baseName = targetPath != null
                ? targetPath.getFileName().toString().replaceFirst("\\.[^.]+$", "")
                : outputId;

        try {
            Path tempPath = Files.createTempFile(tempDir, baseName + "-", extension);
            tempPathsByOutputId.put(outputId, tempPath);
            bindingsById.put(outputId, binding);
            return tempPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary output for " + outputId, e);
        }
    }

    public void commit(String outputId) throws IOException {
        Path tempPath = tempPathsByOutputId.get(outputId);
        OutputBinding binding = bindingsById.get(outputId);
        if (tempPath == null || binding == null) {
            throw new IllegalStateException("No temporary output registered for " + outputId);
        }
        Path targetPath = binding.path();
        if (targetPath == null) {
            throw new IllegalStateException("No target path for output " + outputId);
        }
        Files.createDirectories(targetPath.getParent());
        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        tempPathsByOutputId.remove(outputId);
    }

    public void rollbackAll() {
        for (Path tempPath : tempPathsByOutputId.values()) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignore) {
            }
        }
        tempPathsByOutputId.clear();
        if (!keepTemporaryFiles) {
            try {
                Files.deleteIfExists(tempDir);
            } catch (IOException ignore) {
            }
        }
    }

    @Override
    public void close() {
        rollbackAll();
    }

    public Path tempPath(String outputId) {
        return tempPathsByOutputId.get(outputId);
    }

    public Path tempDir() {
        return tempDir;
    }

    private static String extension(TransferFormat format) {
        if (format == null) return ".xtf";
        return switch (format) {
            case ITF -> ".itf";
            case XTF, XML -> ".xtf";
        };
    }
}
