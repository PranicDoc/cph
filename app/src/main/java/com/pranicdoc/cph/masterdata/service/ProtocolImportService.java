package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportResult;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProtocolImportService {

    private static final String SHEET_NAME = "Protocols";
    private static final String COL_NAME = "Protocol/Technique Name";
    private static final String COL_BOOK = "Book";
    private static final String COL_PAGE = "Page";
    private static final String COL_CATEGORY = "Category";
    private static final String COL_DESCRIPTION = "Description";
    private static final String COL_CHAPTER = "Chapter";
    private static final String COL_TYPE = "Type";

    private final ProtocolCatalogRepository protocolRepo;

    @Transactional
    public ProtocolImportResult importFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String extension = extractExtension(filename);

        List<Map<String, String>> rows = switch (extension) {
            case "xlsx" -> readXlsxRows(file);
            case "csv" -> readCsvRows(file);
            default -> throw new IllegalArgumentException(
                    "Unsupported file type '" + extension + "' — only .xlsx and .csv are accepted");
        };

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;
            String name = trimToNull(row.get(COL_NAME));
            String sourceBook = trimToNull(row.get(COL_BOOK));
            String pageRaw = trimToNull(row.get(COL_PAGE));
            String typeRaw = trimToNull(row.get(COL_TYPE));
            String category = trimToNull(row.get(COL_CATEGORY));
            String description = trimToNull(row.get(COL_DESCRIPTION));
            String chapter = trimToNull(row.get(COL_CHAPTER));

            if (name == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_NAME);
                skipped++;
                continue;
            }
            if (sourceBook == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_BOOK);
                skipped++;
                continue;
            }
            if (pageRaw == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_PAGE);
                skipped++;
                continue;
            }
            Integer pageNumber = parsePageNumber(pageRaw);
            if (pageNumber == null) {
                errors.add("Row " + rowNumber + ": " + COL_PAGE + " '" + pageRaw + "' is not numeric");
                skipped++;
                continue;
            }
            ProtocolType protocolType = parseProtocolType(typeRaw);
            if (protocolType == null) {
                errors.add("Row " + rowNumber + ": invalid " + COL_TYPE + " '" + typeRaw
                        + "' — must be PROTOCOL or SUPPLEMENTARY");
                skipped++;
                continue;
            }

            ProtocolCatalog protocol = protocolRepo
                    .findByNameAndSourceBookAndPageNumber(name, sourceBook, pageNumber)
                    .orElse(null);
            if (protocol == null) {
                protocol = new ProtocolCatalog();
                protocol.setName(name);
                protocol.setSourceBook(sourceBook);
                protocol.setPageNumber(pageNumber);
                protocol.setHealerId(null);
                created++;
            } else {
                updated++;
            }
            protocol.setCategory(category);
            protocol.setDescription(description);
            protocol.setChapter(chapter);
            protocol.setProtocolType(protocolType);
            protocolRepo.save(protocol);
        }

        return new ProtocolImportResult(rows.size(), created, updated, skipped, errors);
    }

    private List<Map<String, String>> readXlsxRows(MultipartFile file) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + SHEET_NAME + "' not found in the uploaded file");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Sheet '" + SHEET_NAME + "' has no header row");
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
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        return cell.toString().trim();
    }

    private Integer parsePageNumber(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ProtocolType parseProtocolType(String raw) {
        if (raw == null) return null;
        try {
            return ProtocolType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
