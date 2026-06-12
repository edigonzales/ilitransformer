package guru.interlis.transformer.dmav;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProduceDm01BbItfTask {

    private static final Path DM01_TO_DMAV_PROFILE = Path.of("profiles/dm01-to-dmav/1.1/bb.yaml");
    private static final Path DMAV_TO_DM01_PROFILE = Path.of("profiles/dmav-to-dm01/1.1/bb.yaml");
    private static final Path DM01_INPUT = Path.of("src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf");
    private static final Path DM01_OUTPUT = Path.of("build/out/dm01-bb.itf");

    private ProduceDm01BbItfTask() {}

    public static void main(String[] args) throws Exception {
        Path dmavIntermediate = Path.of("build/out/dmav-bb-tmp.xtf");

        Files.createDirectories(DM01_OUTPUT.getParent());

        run(materialize(DM01_TO_DMAV_PROFILE, DM01_INPUT, dmavIntermediate),
                Path.of("build/out/reports-dm01-forward"));
        run(materialize(DMAV_TO_DM01_PROFILE, dmavIntermediate, DM01_OUTPUT),
                Path.of("build/out/reports-dmav-reverse"));

        System.out.println("Produced: " + DM01_OUTPUT.toAbsolutePath());
        System.out.println("Size: " + Files.size(DM01_OUTPUT) + " bytes");
    }

    private static void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.defaultModelDirs());
        DiagnosticCollector diagnostics = new JobRunner().run(mappingPath,
                new RunOptions(modelDirs, false, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Diagnostics: " + diagnostics.all());
        }
    }

    private static Path materialize(Path profilePath, Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = outputPath.resolveSibling(outputPath.getFileName() + "-mapping.yaml");
        String yaml = Files.readString(profilePath, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"",
                        "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"input/dmav.xtf\"",
                        "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-bb.xtf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-bb.itf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }
}
