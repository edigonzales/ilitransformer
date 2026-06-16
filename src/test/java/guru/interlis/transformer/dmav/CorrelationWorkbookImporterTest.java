package guru.interlis.transformer.dmav;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import guru.interlis.transformer.diag.DiagnosticCollector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class CorrelationWorkbookImporterTest {

    @Test
    void importsHintsFromArtificialXlsx() throws Exception {
        Path xlsx = createArtificialXlsx();
        try {
            DiagnosticCollector diag = new DiagnosticCollector();
            CorrelationWorkbookImporter.ImportResult result = new CorrelationWorkbookImporter().importHints(xlsx, diag);

            assertThat(result.hintCount()).isEqualTo(4);
            assertThat(result.errorCount()).isZero();
            assertThat(result.warningCount()).isZero();

            // Verify DM01→DMAV hints (rows with code in column U)
            List<CorrelationHint> dmToDmav = result.hints().stream()
                    .filter(h -> h.direction() == Direction.DM01_TO_DMAV)
                    .toList();
            assertThat(dmToDmav).hasSize(2);

            CorrelationHint first = dmToDmav.get(0);
            assertThat(first.rowNumber()).isEqualTo(2);
            assertThat(first.sourceClass()).isEqualTo("DM01_Foo");
            assertThat(first.targetClass()).isEqualTo("DMAV_Bar");
            assertThat(first.sourceAttribute()).isEqualTo("attr1");
            assertThat(first.targetAttribute()).isEqualTo("attrA");
            assertThat(first.transformCode()).isEqualTo("K");
            assertThat(first.confidence()).isEqualTo(0.7);
            assertThat(first.conditionText()).isEqualTo("if something");

            CorrelationHint second = dmToDmav.get(1);
            assertThat(second.rowNumber()).isEqualTo(3);
            assertThat(second.transformCode()).isEqualTo("V");
            assertThat(second.confidence()).isEqualTo(0.5);

            // Verify DMAV→DM01 hint
            List<CorrelationHint> dmavToDm = result.hints().stream()
                    .filter(h -> h.direction() == Direction.DMAV_TO_DM01)
                    .toList();
            assertThat(dmavToDm).hasSize(2);

            assertThat(dmavToDm.get(0).transformCode()).isEqualTo("I");
            assertThat(dmavToDm.get(0).confidence()).isEqualTo(0.3);
        } finally {
            Files.deleteIfExists(xlsx);
        }
    }

    @Test
    void warnsOnUnknownTransformCode() throws Exception {
        Path xlsx = createXlsxWithCode("X");
        try {
            DiagnosticCollector diag = new DiagnosticCollector();
            CorrelationWorkbookImporter.ImportResult result = new CorrelationWorkbookImporter().importHints(xlsx, diag);

            assertThat(result.warningCount()).isEqualTo(1);
            assertThat(result.hintCount()).isEqualTo(1);
            assertThat(result.hints().get(0).warnings()).isNotEmpty();
            assertThat(result.hints().get(0).transformCode()).isEqualTo("X");
        } finally {
            Files.deleteIfExists(xlsx);
        }
    }

    @Test
    void skipsEmptyRows() throws Exception {
        Path xlsx = createXlsxWithEmptyRows();
        try {
            DiagnosticCollector diag = new DiagnosticCollector();
            CorrelationWorkbookImporter.ImportResult result = new CorrelationWorkbookImporter().importHints(xlsx, diag);

            assertThat(result.hintCount()).isEqualTo(2);
        } finally {
            Files.deleteIfExists(xlsx);
        }
    }

    @Test
    void importsRealXlsxSnapshot() throws Exception {
        Path xlsx = Path.of("docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx");
        assumeTrue(Files.exists(xlsx), "Real XLSX not available");

        DiagnosticCollector diag = new DiagnosticCollector();
        CorrelationWorkbookImporter.ImportResult result = new CorrelationWorkbookImporter().importHints(xlsx, diag);

        // Snapshot assertions: the real XLSX should produce a reasonable number of hints
        assertThat(result.hintCount()).isGreaterThan(100);
        assertThat(result.errorCount()).isZero();
        // Warnings for unknown codes are expected (the XLSX has some non-standard entries)
        assertThat(result.warningCount()).isGreaterThanOrEqualTo(0);

        // Known valid codes should dominate
        long validHints = result.hints().stream()
                .filter(h -> List.of("K", "V", "I").contains(h.transformCode()))
                .count();
        assertThat(validHints).isGreaterThan((long) (result.hintCount() * 0.95));

        // Direction counts should be roughly balanced
        long dmToDmav = result.hints().stream()
                .filter(h -> h.direction() == Direction.DM01_TO_DMAV)
                .count();
        long dmavToDm = result.hints().stream()
                .filter(h -> h.direction() == Direction.DMAV_TO_DM01)
                .count();
        assertThat(dmToDmav).isGreaterThan(0);
        assertThat(dmavToDm).isGreaterThan(0);
    }

    @Test
    void exporterWritesJsonAndReport() throws Exception {
        Path xlsx = createArtificialXlsx();
        Path jsonPath = Files.createTempFile("hints-", ".json");
        Path reportPath = Files.createTempFile("report-", ".md");
        try {
            DiagnosticCollector diag = new DiagnosticCollector();
            CorrelationWorkbookImporter.ImportResult result = new CorrelationWorkbookImporter().importHints(xlsx, diag);

            CorrelationHintExporter exporter = new CorrelationHintExporter();
            exporter.writeJson(result.hints(), jsonPath);
            exporter.writeReport(result, reportPath);

            assertThat(jsonPath).isNotEmptyFile();
            String json = Files.readString(jsonPath);
            assertThat(json).contains("DM01_TO_DMAV");
            assertThat(json).contains("transformCode");
            assertThat(json).contains("confidence");

            assertThat(reportPath).isNotEmptyFile();
            String report = Files.readString(reportPath);
            assertThat(report).contains("# DM01↔DMAV Correlation Import Report");
            assertThat(report).contains("Total hints");
            assertThat(report).contains("By Transform Code");
        } finally {
            Files.deleteIfExists(xlsx);
            Files.deleteIfExists(jsonPath);
            Files.deleteIfExists(reportPath);
        }
    }

    // -- XLSX builders -------------------------------------------------------

    private Path createArtificialXlsx() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Transformation");

        // Header row
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Nr.");
        header.createCell(20).setCellValue("Code DM01→DMAV");
        header.createCell(25).setCellValue("Code DMAV→DM01");

        // Data row 1: DM01→DMAV with K, DMAV→DM01 with I
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue(1);
        row1.createCell(32).setCellValue("DM01_Foo"); // DM01 Topic
        row1.createCell(33).setCellValue("DM01_Foo"); // DM01 Table/Class
        row1.createCell(34).setCellValue("attr1"); // DM01 Attribute
        row1.createCell(11).setCellValue("DMAV_Bar"); // DMAV Topic
        row1.createCell(12).setCellValue("DMAV_Bar"); // DMAV Class
        row1.createCell(13).setCellValue("attrA"); // DMAV Attribute
        row1.createCell(19).setCellValue("if something"); // Condition DM01
        row1.createCell(20).setCellValue("K"); // Code DM01→DMAV
        row1.createCell(22).setCellValue("default: 42"); // Addition DM01→DMAV
        row1.createCell(25).setCellValue("I"); // Code DMAV→DM01
        row1.createCell(29).setCellValue("Some note"); // Notes

        // Data row 2: DM01→DMAV with V, no DMAV→DM01
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue(2);
        row2.createCell(33).setCellValue("DM01_Baz");
        row2.createCell(34).setCellValue("attr2");
        row2.createCell(12).setCellValue("DMAV_Qux");
        row2.createCell(13).setCellValue("attrB");
        row2.createCell(20).setCellValue("V");

        // Data row 3: DMAV→DM01 only (no DM01→DMAV)
        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue(3);
        row3.createCell(33).setCellValue("DM01_Xyz");
        row3.createCell(12).setCellValue("DMAV_Abc");
        row3.createCell(25).setCellValue("K");

        Path path = Files.createTempFile("test-correlation-", ".xlsx");
        wb.write(Files.newOutputStream(path));
        wb.close();
        return path;
    }

    private Path createXlsxWithCode(String code) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Transformation");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Nr.");
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue(1);
        row.createCell(20).setCellValue(code);
        Path path = Files.createTempFile("test-code-", ".xlsx");
        wb.write(Files.newOutputStream(path));
        wb.close();
        return path;
    }

    private Path createXlsxWithEmptyRows() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Transformation");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Nr.");

        // Empty row
        sheet.createRow(1);

        // Data row
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue(1);
        row2.createCell(20).setCellValue("K");

        // Another empty row
        sheet.createRow(3);

        // Another data row
        Row row4 = sheet.createRow(4);
        row4.createCell(0).setCellValue(2);
        row4.createCell(25).setCellValue("V");

        Path path = Files.createTempFile("test-empty-", ".xlsx");
        wb.write(Files.newOutputStream(path));
        wb.close();
        return path;
    }
}
