package guru.interlis.transformer.dmav;

import guru.interlis.transformer.model.ExtractionRequest;

import java.nio.file.Path;
import java.util.List;

public final class Dm01DmavFixtures {

    private static final List<String> LFP3_TARGET_CLASSES =
            List.of("LFP3Nachfuehrung", "LFP3", "LFP3Pos", "LFP3Symbol");

    private Dm01DmavFixtures() {}

    public static ExtractionRequest lfp3ExtractionRequest(Path targetDir, List<String> modelDirs) {
        return new ExtractionRequest(
                LFP3_TARGET_CLASSES,
                modelDirs,
                2,
                200,
                true,
                targetDir
        );
    }

    public static boolean isLfp3RelevantClass(String className) {
        if (className == null) {
            return false;
        }
        String upper = className.toUpperCase();
        return upper.contains("LFP3") || upper.contains("FIXPUNKT");
    }
}
