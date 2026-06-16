package guru.interlis.transformer.feature;

import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ExpressionFunctionReferenceTask {

    private ExpressionFunctionReferenceTask() {}

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

        if (markdownOut == null && jsonOut == null) {
            System.err.println("Usage: ExpressionFunctionReferenceTask --markdown <path> --json <path>");
            System.exit(1);
        }

        FunctionRegistry registry = FunctionRegistry.defaultRegistry();

        if (markdownOut != null) {
            writeMarkdown(registry, Path.of(markdownOut));
            System.out.println("Expression function reference written to " + markdownOut);
        }
        if (jsonOut != null) {
            writeJson(registry, Path.of(jsonOut));
            System.out.println("Expression function reference written to " + jsonOut);
        }
    }

    private static void writeMarkdown(FunctionRegistry registry, Path path) throws Exception {
        Map<String, FunctionDef> functions = new TreeMap<>(registry.all());

        StringBuilder sb = new StringBuilder();
        sb.append("# Expression Function Reference\n\n");
        sb.append("Generated from `FunctionRegistry.defaultRegistry()`. Do not edit manually.\n\n");
        sb.append("| Function | Parameters | Return Type | Deterministic |\n");
        sb.append("|---|---|---|---|\n");

        for (FunctionDef def : functions.values()) {
            List<String> paramDescs = def.parameters().stream()
                    .map(p -> p.name() + ": " + p.type().name())
                    .toList();
            String paramStr = String.join(", ", paramDescs);
            sb.append("| `")
                    .append(def.name())
                    .append("` | `(")
                    .append(paramStr)
                    .append(")` | ")
                    .append(def.returnType().name())
                    .append(" | ")
                    .append(def.deterministic() ? "yes" : "no")
                    .append(" |\n");
        }

        Files.createDirectories(path.getParent());
        Files.writeString(path, sb.toString());
    }

    private static void writeJson(FunctionRegistry registry, Path path) throws Exception {
        Map<String, FunctionDef> functions = new TreeMap<>(registry.all());

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        boolean first = true;
        for (FunctionDef def : functions.values()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  { \"name\": \"").append(def.name()).append("\", \"parameters\": [");
            for (int i = 0; i < def.parameters().size(); i++) {
                if (i > 0) sb.append(", ");
                var p = def.parameters().get(i);
                sb.append("{\"name\": \"")
                        .append(p.name())
                        .append("\", \"type\": \"")
                        .append(p.type().name())
                        .append("\"}");
            }
            sb.append("], \"returnType\": \"")
                    .append(def.returnType().name())
                    .append("\", \"deterministic\": ")
                    .append(def.deterministic())
                    .append(" }");
        }
        sb.append("\n]\n");

        Files.createDirectories(path.getParent());
        Files.writeString(path, sb.toString());
    }
}
