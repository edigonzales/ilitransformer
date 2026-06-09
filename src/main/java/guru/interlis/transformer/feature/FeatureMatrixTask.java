package guru.interlis.transformer.feature;

import java.nio.file.Path;

public final class FeatureMatrixTask {

    private FeatureMatrixTask() {}

    public static void main(String[] args) throws Exception {
        String markdownOut = null;
        String jsonOut = null;

        for (int i = 0; i < args.length; i++) {
            if ("--markdown".equals(args[i]) && i + 1 < args.length) {
                markdownOut = args[++i];
            } else if ("--json".equals(args[i]) && i + 1 < args.length) {
                jsonOut = args[++i];
            }
        }

        var matrix = new FeatureMatrix();

        if (markdownOut != null) {
            matrix.writeMarkdown(Path.of(markdownOut));
            System.out.println("Feature matrix written to " + markdownOut);
        }
        if (jsonOut != null) {
            matrix.writeJson(Path.of(jsonOut));
            System.out.println("Feature matrix written to " + jsonOut);
        }

        if (markdownOut == null && jsonOut == null) {
            System.err.println("Usage: FeatureMatrixTask --markdown <path> --json <path>");
            System.exit(1);
        }
    }
}
