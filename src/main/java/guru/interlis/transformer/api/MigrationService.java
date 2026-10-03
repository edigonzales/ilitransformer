package guru.interlis.transformer.api;

import guru.interlis.transformer.app.*;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.model.ModelRegistry;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** Embeddable, UI-independent entry point for XTF model migrations. */
public final class MigrationService {
    public record Request(
            Path mapping,
            Path input,
            Path output,
            List<String> modelDirectories,
            boolean validate,
            boolean overwrite,
            Path reportDirectory) {
        public Request {
            modelDirectories = modelDirectories == null ? List.of() : List.copyOf(modelDirectories);
        }
    }

    public record Prepared(PreparedJob job, RunOptions options, List<Path> inputs, Path output) {}

    public Prepared prepare(Request request) throws Exception {
        Path base = request.mapping().toAbsolutePath().getParent();
        var config = new MappingLoader().load(request.mapping());
        if (config.job.inputs.size() != 1 || config.job.outputs.size() != 1)
            throw new IllegalArgumentException("Migration requires one XTF input and one XTF output");
        var input = config.job.inputs.getFirst();
        var output = config.job.outputs.getFirst();

        input.path = resolve(base, request.input() != null ? request.input().toString() : input.path)
                .toString();
        output.path = resolve(base, request.output() != null ? request.output().toString() : output.path)
                .toString();
        input.format = requireXtf(input.format, input.path);
        output.format = requireXtf(output.format, output.path);
        List<String> dirs = new ArrayList<>();
        if (config.job.modeldir != null) dirs.addAll(config.job.modeldir);
        dirs.addAll(request.modelDirectories());
        dirs = dirs.stream()
                .map(d -> d.contains("://") ? d : base.resolve(d).normalize().toString())
                .distinct()
                .toList();
        config.job.modeldir = dirs;
        var registry = ModelRegistry.builder()
                .config(config)
                .modelDirs(dirs)
                .baseDirectory(base)
                .build();
        var plan = new MappingCompiler().compileTyped(config, registry).withFailPolicy(FailPolicy.STRICT);
        return new Prepared(
                new PreparedJob(plan, registry, base),
                new RunOptions(dirs, request.validate(), request.reportDirectory(), false, FailPolicy.STRICT),
                List.of(Path.of(input.path)),
                Path.of(output.path));
    }

    public DiagnosticCollector execute(Request request, Consumer<String> log) throws Exception {
        log.accept("Compiling models and mapping");
        var config = new MappingLoader().load(request.mapping());
        if (config.job.inputs.stream()
                .anyMatch(i -> i.options != null && "false".equals(i.options.get("migrationReviewed"))))
            throw new IllegalArgumentException(
                    "Review the migration proposals and information losses before execution; then set migrationReviewed to true");
        var prepared = prepare(request);
        if (prepared.job().plan().diagnostics().hasErrors())
            return prepared.job().plan().diagnostics();
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        for (Path input : prepared.inputs()) {
            requireDistinct(input, prepared.output());
            originals.put(input, digest(input));
        }
        if (!request.overwrite() && Files.exists(prepared.output()))
            throw new IOException("Target already exists (overwrite disabled): " + prepared.output());
        var runner = new JobRunner(log);
        runner.setPublicationPolicy(request.overwrite(), true);
        runner.setBeforeCommit(() -> {
            try {
                for (var entry : originals.entrySet()) {
                    requireDistinct(entry.getKey(), prepared.output());
                    if (!Arrays.equals(entry.getValue(), digest(entry.getKey())))
                        throw new IOException("Original transfer changed during migration: " + entry.getKey());
                }
                if (!request.overwrite() && Files.exists(prepared.output()))
                    throw new IOException("Target appeared during migration: " + prepared.output());
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
        return runner.runPrepared(prepared.job(), prepared.options());
    }

    public static void requireDistinct(Path input, Path output) throws IOException {
        Path original = input.toRealPath();
        Path target = output.toAbsolutePath().normalize();
        if (Files.exists(target) && Files.isSameFile(original, target))
            throw new IOException("Original and target must be different files: " + output);
        Path parent = target.getParent();
        if (parent != null
                && Files.exists(parent)
                && original.equals(parent.toRealPath().resolve(target.getFileName())))
            throw new IOException("Original and target must be different files: " + output);
    }

    private static Path resolve(Path base, String path) {
        if (path == null || path.isBlank() || path.contains("${"))
            throw new IllegalArgumentException("Missing or unresolved transfer path: " + path);
        return base.resolve(path).toAbsolutePath().normalize();
    }

    private static String requireXtf(String format, String path) {
        if (format != null && !format.equalsIgnoreCase("xtf"))
            throw new IllegalArgumentException("Migration supports XTF, not " + format);
        if (!path.toLowerCase(Locale.ROOT).endsWith(".xtf")
                && !path.toLowerCase(Locale.ROOT).endsWith(".xml"))
            throw new IllegalArgumentException("XTF file expected: " + path);
        return "xtf";
    }

    private static byte[] digest(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = stream.read(buffer)) >= 0) {
                    guru.interlis.transformer.engine.ExecutionCancellation.check();
                    digest.update(buffer, 0, count);
                }
            }
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
