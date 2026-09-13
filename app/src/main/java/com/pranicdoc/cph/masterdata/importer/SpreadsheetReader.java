package com.pranicdoc.cph.masterdata.importer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared xlsx/csv row parsing, extracted verbatim from {@code
 * ProtocolImportService}'s former private methods (readXlsxRows/readCsvRows/
 * cellToString/extractExtension), so every masterdata importer this phase
 * introduces (Protocol/PranaColor/Chakra/ProtocolColorMapping) reads its own
 * sheet through one shared component instead of re-implementing parsing.
 *
 * <p>Purely file-parsing — no domain-entity coupling. The one deviation from
 * the original private methods: {@code readXlsxRows} now takes an explicit
 * {@code sheetName} parameter instead of a hardcoded {@code SHEET_NAME}
 * constant, since each importer has its own sheet name ("Protocols",
 * "PranaColors", "Chakras", "ProtocolColorMappings").
 */
@Component
public class SpreadsheetReader {

    public List<Map<String, String>> read(MultipartFile file, String sheetName) {
        return read(file, List.of(sheetName));
    }

    /**
     * Tries each candidate sheet name in order, using the first one that
     * exists in the workbook — a real-world upload's sheet name doesn't
     * always match a single importer's expected name (e.g. a workbook
     * exported with several generically-named sheets, only one of which is
     * the intended data). Falls back to the workbook's first sheet only if
     * NONE of the candidates are present.
     */
    public List<Map<String, String>> read(MultipartFile file, List<String> candidateSheetNames) {
        String extension = extractExtension(file.getOriginalFilename());
        return switch (extension) {
            case "xlsx" -> readXlsxRows(file, candidateSheetNames);
            case "csv" -> readCsvRows(file);
            default -> throw new IllegalArgumentException(
                    "Unsupported file type '" + extension + "' — only .xlsx and .csv are accepted");
        };
    }

    private List<Map<String, String>> readXlsxRows(MultipartFile file, List<String> candidateSheetNames) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = null;
            for (String candidate : candidateSheetNames) {
                sheet = workbook.getSheet(candidate);
                if (sheet != null) break;
            }
            if (sheet == null) {
                // Fallback: none of the candidate names matched (e.g. a workbook
                // exported with a generic "Sheet2" name the importer didn't
                // anticipate). Rather than fail the whole import, fall back to the
                // workbook's first sheet — the header-row-driven column mapping
                // below still applies regardless of which sheet supplied the rows.
                if (workbook.getNumberOfSheets() == 0) {
                    throw new IllegalArgumentException(
                            "None of the expected sheets " + candidateSheetNames + " found in the uploaded file");
                }
                sheet = workbook.getSheetAt(0);
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Sheet '" + sheet.getSheetName() + "' has no header row");
            }
            Map<Integer, String> headerByIndex = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                headerByIndex.put(cell.getColumnIndex(), cellToString(cell));
            }
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headerByIndex.entrySet()) {
                    Cell cell = row.getCell(entry.getKey());
                    rowMap.put(entry.getValue(), cell == null ? null : cellToString(cell));
                }
                rows.add(rowMap);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read xlsx file: " + e.getMessage());
        }
        return rows;
    }

    private List<Map<String, String>> readCsvRows(MultipartFile file) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                record.toMap().forEach(rowMap::put);
                rows.add(rowMap);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read csv file: " + e.getMessage());
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> formatNumeric(cell.getNumericCellValue());
            // WR-05: a formula cell's Cell#toString() returns the formula text itself
            // (e.g. "=A2&\" \"&B2"), not its evaluated value — reading the cached
            // formula result instead avoids writing the literal formula string into a
            // masterdata row's field.
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> formatNumeric(cell.getNumericCellValue());
                case STRING -> cell.getStringCellValue().trim();
                default -> throw new IllegalArgumentException(
                        "Unsupported formula result type in cell " + cell.getAddress());
            };
            default -> cell.toString().trim();
        };
    }

    private String formatNumeric(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
