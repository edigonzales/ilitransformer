package guru.interlis.transformer.cli;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.InventorySerializer;
import guru.interlis.transformer.model.ModelInventory;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "inspect-model",
        description = "Compile an INTERLIS model and output its inventory as JSON and/or Markdown",
        mixinStandardHelpOptions = true)
public final class InspectModelCommand implements Callable<Integer> {

    @Option(
            names = {"--model"},
            required = true,
            description = "INTERLIS model name (file path or qualified model name)")
    private String model;

    @Option(
            names = {"--modeldir"},
            description = "Model directory path(s) for INTERLIS model resolution (semicolon-separated)")
    private String modelDir;

    @Option(
            names = {"--output"},
            description = "Output file path base (extensions .json and .md will be appended)")
    private Path output;

    @Option(
            names = {"--format"},
            description = "Output format: json, markdown, or both (default: both)")
    private String format = "both";

    @Override
    public Integer call() throws Exception {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel(model, modelDir);

        if (result.hasErrors()) {
            for (Diagnostic d : result.diagnostics().all()) {
                System.err.printf("[%s] %s: %s%n", d.severity(), d.code(), d.message());
            }
            return 1;
        }

        if (result.transferDescription() == null) {
            System.err.println("Error: No model data available.");
            return 1;
        }

        TypeSystemFacade facade = new TypeSystemFacade(result.transferDescription());
        List<ModelInventory> inventories = facade.listAllModels();
        InventorySerializer serializer = new InventorySerializer();

        String formatLower = format != null ? format.toLowerCase() : "both";
        boolean doJson = formatLower.equals("both") || formatLower.equals("json");
        boolean doMd = formatLower.equals("both") || formatLower.equals("markdown") || formatLower.equals("md");

        if (output != null) {
            String basePath = output.toString();
            if (basePath.endsWith(".json") || basePath.endsWith(".md")) {
                // strip extension
                int dot = basePath.lastIndexOf('.');
                basePath = basePath.substring(0, dot);
            }
            if (doJson) {
                serializer.writeJson(inventories, Path.of(basePath + ".json"));
                System.out.println("JSON inventory written to: " + basePath + ".json");
            }
            if (doMd) {
                serializer.writeMarkdown(inventories, Path.of(basePath + ".md"));
                System.out.println("Markdown inventory written to: " + basePath + ".md");
            }
        } else {
            if (doJson) {
                System.out.println(serializer.toJson(inventories));
            }
            if (doMd) {
                if (doJson) System.out.println("\n---\n");
                System.out.println(serializer.toMarkdown(inventories));
            }
        }

        if (!result.diagnostics().all().isEmpty()) {
            System.err.println("\nWarnings:");
            for (Diagnostic d : result.diagnostics().all()) {
                System.err.printf("  [%s] %s: %s%n", d.severity(), d.code(), d.message());
            }
        }

        return 0;
    }
}
