package guru.interlis.transformer.dmav;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;

import java.nio.file.Path;

public final class CorrelationImportTask {

    public static void main(String[] args) throws Exception {
        Path xlsx = null;
        Path out = Path.of("build/generated/dm01-dmav/correlation-hints.json");
        Path report = Path.of("build/reports/dm01-dmav/correlation-import-report.md");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--xlsx" -> xlsx = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
            }
        }

        if (xlsx == null) {
            System.err.println("Usage: CorrelationImportTask --xlsx <path> [--out <path>] [--report <path>]");
            System.exit(1);
        }

        if (!xlsx.toFile().exists()) {
            System.err.println("XLSX file not found: " + xlsx);
            System.exit(1);
        }

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        CorrelationWorkbookImporter importer = new CorrelationWorkbookImporter();
        CorrelationWorkbookImporter.ImportResult result = importer.importHints(xlsx, diagnostics);
        CorrelationHintExporter exporter = new CorrelationHintExporter();

        exporter.writeJson(result.hints(), out);
        exporter.writeReport(result, report);

        System.out.println("Imported " + result.hintCount() + " correlation hints");
        System.out.println("  DM01→DMAV: "
                + result.hints().stream()
                        .filter(h -> h.direction() == Direction.DM01_TO_DMAV)
                        .count());
        System.out.println("  DMAV→DM01: "
                + result.hints().stream()
                        .filter(h -> h.direction() == Direction.DMAV_TO_DM01)
                        .count());
        System.out.println("  Errors:   " + result.errorCount());
        System.out.println("  Warnings: " + result.warningCount());
        System.out.println("JSON:  " + out);
        System.out.println("Report: " + report);

        for (Diagnostic d : result.diagnostics().all()) {
            if (d.severity() == Severity.WARNING) {
                System.out.println("[WARN] " + d.message() + " (" + d.sourcePath() + ")");
            }
        }

        if (result.errorCount() > 0) {
            System.exit(1);
        }
    }
}
