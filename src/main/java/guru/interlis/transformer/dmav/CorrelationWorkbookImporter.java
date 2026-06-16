package guru.interlis.transformer.dmav;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;

public final class CorrelationWorkbookImporter {

    private static final String MAIN_SHEET = "Transformation";
    private static final Set<String> VALID_CODES = Set.of("K", "V", "I");

    // Column indices (0-based) for Sheet "Transformation"
    private static final int COL_NR = 0; // A
    private static final int COL_DMAV_TYPE = 10; // K
    private static final int COL_DMAV_TOPIC = 11; // L
    private static final int COL_DMAV_CLASS = 12; // M
    private static final int COL_DMAV_ATTR = 13; // N
    private static final int COL_DMAV_STRUCT = 14; // O
    private static final int COL_DMAV_SUBSTR = 15; // P
    private static final int COL_DMAV_COMMENT = 17; // R
    private static final int COL_COND_DM01 = 19; // T
    private static final int COL_CODE_DM01_DMAV = 20; // U
    private static final int COL_TARGET_DM01_DMAV = 21; // V
    private static final int COL_ADD_DM01_DMAV = 22; // W
    private static final int COL_COND_DMAV = 24; // Y
    private static final int COL_CODE_DMAV_DM01 = 25; // Z
    private static final int COL_TARGET_DMAV_DM01 = 26; // AA
    private static final int COL_ADD_DMAV_DM01 = 27; // AB
    private static final int COL_NOTES = 29; // AD
    private static final int COL_DM01_LINK = 31; // AF
    private static final int COL_DM01_TOPIC = 32; // AG
    private static final int COL_DM01_TABLE = 33; // AH
    private static final int COL_DM01_ATTR = 34; // AI

    public ImportResult importHints(Path xlsxPath, DiagnosticCollector diagnostics) throws IOException {
        List<CorrelationHint> hints = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(xlsxPath.toFile())) {
            Sheet sheet = workbook.getSheet(MAIN_SHEET);
            if (sheet == null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.DMAV_CORRELATION_PARSE,
                        Severity.ERROR,
                        "Sheet not found: " + MAIN_SHEET,
                        xlsxPath.toString(),
                        "Expected sheet name: " + MAIN_SHEET));
                return new ImportResult(hints, diagnostics);
            }

            int lastRowNum = sheet.getLastRowNum();
            for (int i = 0; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                // Row 0 is the header
                if (i == 0) continue;

                String nr = cellString(row, COL_NR);
                if (nr == null || nr.isBlank()) continue;

                List<String> rowWarnings = new ArrayList<>();

                // DM01→DMAV direction
                String codeDm01Dmav = cellString(row, COL_CODE_DM01_DMAV);
                if (codeDm01Dmav != null && !codeDm01Dmav.isBlank()) {
                    codeDm01Dmav = codeDm01Dmav.trim();
                    if (!VALID_CODES.contains(codeDm01Dmav)) {
                        rowWarnings.add("Unknown transform code DM01→DMAV: " + codeDm01Dmav);
                        diagnostics.add(new Diagnostic(
                                DiagnosticCode.DMAV_CORRELATION_PARSE,
                                Severity.WARNING,
                                "Unknown transform code in row " + (i + 1) + ": " + codeDm01Dmav,
                                cellRef(MAIN_SHEET, i, COL_CODE_DM01_DMAV),
                                "Valid codes: K, V, I"));
                    }
                    hints.add(createHint(
                            i,
                            Direction.DM01_TO_DMAV,
                            cellString(row, COL_DM01_TOPIC),
                            cellString(row, COL_DM01_TABLE),
                            cellString(row, COL_DM01_ATTR),
                            cellString(row, COL_DMAV_TOPIC),
                            cellString(row, COL_DMAV_CLASS),
                            cellString(row, COL_DMAV_ATTR),
                            buildTargetPath(row),
                            cellString(row, COL_COND_DM01),
                            codeDm01Dmav,
                            cellString(row, COL_ADD_DM01_DMAV),
                            cellString(row, COL_NOTES),
                            codeConfidence(codeDm01Dmav),
                            rowWarnings));
                }

                // DMAV→DM01 direction
                String codeDmavDm01 = cellString(row, COL_CODE_DMAV_DM01);
                if (codeDmavDm01 != null && !codeDmavDm01.isBlank()) {
                    codeDmavDm01 = codeDmavDm01.trim();
                    List<String> revWarnings = new ArrayList<>();
                    if (!VALID_CODES.contains(codeDmavDm01)) {
                        revWarnings.add("Unknown transform code DMAV→DM01: " + codeDmavDm01);
                        diagnostics.add(new Diagnostic(
                                DiagnosticCode.DMAV_CORRELATION_PARSE,
                                Severity.WARNING,
                                "Unknown transform code in row " + (i + 1) + ": " + codeDmavDm01,
                                cellRef(MAIN_SHEET, i, COL_CODE_DMAV_DM01),
                                "Valid codes: K, V, I"));
                    }
                    hints.add(createHint(
                            i,
                            Direction.DMAV_TO_DM01,
                            cellString(row, COL_DMAV_TOPIC),
                            cellString(row, COL_DMAV_CLASS),
                            cellString(row, COL_DMAV_ATTR),
                            cellString(row, COL_DM01_TOPIC),
                            cellString(row, COL_DM01_TABLE),
                            cellString(row, COL_DM01_ATTR),
                            cellString(row, COL_TARGET_DMAV_DM01),
                            cellString(row, COL_COND_DMAV),
                            codeDmavDm01,
                            cellString(row, COL_ADD_DMAV_DM01),
                            cellString(row, COL_NOTES),
                            codeConfidence(codeDmavDm01),
                            revWarnings));
                }
            }
        }

        return new ImportResult(hints, diagnostics);
    }

    private CorrelationHint createHint(
            int rowIdx,
            Direction direction,
            String srcTopic,
            String srcClass,
            String srcAttr,
            String tgtTopic,
            String tgtClass,
            String tgtAttr,
            String tgtPath,
            String condition,
            String code,
            String addition,
            String comment,
            double confidence,
            List<String> warnings) {
        return new CorrelationHint(
                rowIdx + 1, // 1-based row number
                MAIN_SHEET,
                cellRef(MAIN_SHEET, rowIdx, COL_CODE_DM01_DMAV),
                direction,
                srcTopic,
                srcClass,
                srcAttr,
                tgtTopic,
                tgtClass,
                tgtAttr,
                tgtPath,
                condition,
                code,
                addition,
                comment,
                confidence,
                warnings);
    }

    private String buildTargetPath(Row row) {
        String struct = cellString(row, COL_DMAV_STRUCT);
        String substr = cellString(row, COL_DMAV_SUBSTR);
        if (struct == null && substr == null) return null;
        StringBuilder sb = new StringBuilder();
        if (struct != null && !struct.isBlank()) sb.append(struct);
        if (substr != null && !substr.isBlank()) {
            if (!sb.isEmpty()) sb.append(".");
            sb.append(substr);
        }
        return !sb.isEmpty() ? sb.toString() : null;
    }

    private double codeConfidence(String code) {
        if (code == null) return 0.5;
        return switch (code.trim()) {
            case "K" -> 0.7;
            case "V" -> 0.5;
            case "I" -> 0.3;
            default -> 0.5;
        };
    }

    private String cellString(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.STRING) {
            String value = cell.getStringCellValue();
            return value != null ? value.trim() : null;
        }
        if (type == CellType.NUMERIC) {
            double num = cell.getNumericCellValue();
            if (num == Math.floor(num) && !Double.isInfinite(num)) {
                return String.valueOf((long) num);
            }
            return String.valueOf(num);
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (type == CellType.FORMULA) {
            try {
                return cell.getStringCellValue().trim();
            } catch (Exception e) {
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK && cell.getCellType() != CellType._NONE) {
                String val = cellString(row, i);
                if (val != null && !val.isBlank()) return false;
            }
        }
        return true;
    }

    private static String cellRef(String sheet, int rowIdx, int colIdx) {
        return sheet + "!" + CellReference.convertNumToColString(colIdx) + (rowIdx + 1);
    }

    public record ImportResult(List<CorrelationHint> hints, DiagnosticCollector diagnostics) {
        public int hintCount() {
            return hints.size();
        }

        public long errorCount() {
            return diagnostics.errors();
        }

        public long warningCount() {
            return diagnostics.warnings();
        }
    }
}
