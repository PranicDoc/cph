package com.pranicdoc.cph.masterdata.importer;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure file-parsing tests for SpreadsheetReader — no domain-entity coupling
 * (no ProtocolCatalog/ProtocolCatalogRepository/ProtocolImportService
 * references), since SpreadsheetReader is shared across all four future
 * importers.
 */
class SpreadsheetReaderTest {

    private final SpreadsheetReader reader = new SpreadsheetReader();

    private MockMultipartFile buildXlsx(String sheetName, String[] headers, List<Object[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Object[] values = rows.get(r);
                for (int c = 0; c < values.length; c++) {
                    if (values[c] == null) {
                        continue;
                    } else if (values[c] instanceof Double d) {
                        row.createCell(c).setCellValue(d);
                    } else {
                        row.createCell(c).setCellValue(values[c].toString());
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "data.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private MockMultipartFile buildCsv(String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder(String.join(",", headers)).append("\n");
        for (String[] values : rows) {
            sb.append(String.join(",", values)).append("\n");
        }
        return new MockMultipartFile("file", "data.csv", "text/csv",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void read_xlsxHappyPath_returnsOneRowMapPerDataRow() throws IOException {
        MockMultipartFile file = buildXlsx("Chakras",
                new String[]{"Code", "Name"},
                List.of(new Object[]{"CROWN", "Crown Chakra"}, new Object[]{"HEART", "Heart Chakra"}));

        List<Map<String, String>> rows = reader.read(file, "Chakras");

        assertEquals(2, rows.size());
        assertEquals("CROWN", rows.get(0).get("Code"));
        assertEquals("Crown Chakra", rows.get(0).get("Name"));
        assertEquals("HEART", rows.get(1).get("Code"));
        assertEquals("Heart Chakra", rows.get(1).get("Name"));
    }

    @Test
    void read_csvHappyPath_returnsOneRowMapPerDataLine_sheetNameIgnored() {
        MockMultipartFile file = buildCsv(
                new String[]{"Code", "Name"},
                List.of(new String[]{"CROWN", "Crown Chakra"}, new String[]{"HEART", "Heart Chakra"}));

        List<Map<String, String>> rows = reader.read(file, "IrrelevantSheetName");

        assertEquals(2, rows.size());
        assertEquals("CROWN", rows.get(0).get("Code"));
        assertEquals("Heart Chakra", rows.get(1).get("Name"));
    }

    @Test
    void read_xlsxMissingSheet_throwsIllegalArgumentExceptionContainingSheetName() throws IOException {
        MockMultipartFile file = buildXlsx("Chakras", new String[]{"Code"}, List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(file, "DoesNotExist"));

        assertTrue(ex.getMessage().contains("DoesNotExist"));
    }

    @Test
    void read_unsupportedExtension_throwsIllegalArgumentExceptionMentioningUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "data.txt", "text/plain",
                "irrelevant".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(file, "AnySheet"));

        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void read_xlsxNumericCell_wholeNumberRoundTripsWithoutTrailingDecimal() throws IOException {
        MockMultipartFile file = buildXlsx("Sheet1",
                new String[]{"Page"},
                List.of(new Object[]{45.0}));

        List<Map<String, String>> rows = reader.read(file, "Sheet1");

        assertEquals("45", rows.get(0).get("Page"));
    }
}
