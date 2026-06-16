package guru.interlis.transformer.cli;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.dmav.CorrelationHintExporter;
import guru.interlis.transformer.dmav.CorrelationWorkbookImporter;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "import-correlation",
        description = "Import DM01/DMAV correlation hints from XLSX",
        mixinStandardHelpOptions = true)
public final class ImportCorrelationCommand implements Callable<Integer> {

    @Option(
            names = {"--xlsx"},
            required = true,
            description = "Path to the correlation XLSX file")
    private Path xlsx;

    @Option(
            names = {"--out"},
            description =
                    "Output path for correlation-hints.json (default: build/generated/dm01-dmav/correlation-hints.json)")
    private Path out = Path.of("build/generated/dm01-dmav/correlation-hints.json");

    @Option(
            names = {"--report"},
            description =
                    "Output path for import report (default: build/reports/dm01-dmav/correlation-import-report.md)")
    private Path report = Path.of("build/reports/dm01-dmav/correlation-import-report.md");

    @Override
    public Integer call() throws Exception {
        if (!xlsx.toFile().exists()) {
            System.err.println("XLSX file not found: " + xlsx);
            return 1;
        }

        out.getParent().toFile().mkdirs();
        report.getParent().toFile().mkdirs();

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        CorrelationWorkbookImporter importer = new CorrelationWorkbookImporter();
        CorrelationWorkbookImporter.ImportResult result = importer.importHints(xlsx, diagnostics);
        CorrelationHintExporter exporter = new CorrelationHintExporter();

        exporter.writeJson(result.hints(), out);
        exporter.writeReport(result, report);

        System.out.println("Imported " + result.hintCount() + " correlation hints");
        System.out.println("  JSON:   " + out);
        System.out.println("  Report: " + report);

        for (Diagnostic d : result.diagnostics().all()) {
            if (d.severity() == Severity.WARNING) {
                System.out.println("[WARN] " + d.message() + " (" + d.sourcePath() + ")");
            }
        }

        return result.errorCount() > 0 ? 1 : 0;
    }
}
