package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChakraImportServiceTest {

    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private MasterdataColumnConfigRepository columnConfigRepo;
    @Spy private SpreadsheetReader spreadsheetReader = new SpreadsheetReader();

    private ChakraImportService importService;

    private static final String[] HEADERS = {"Code", "Name", "Category", "Location"};

    @BeforeEach
    void setUp() {
        importService = new ChakraImportService(chakraRepo, spreadsheetReader, columnConfigRepo);
        lenient().when(chakraRepo.save(any(ChakraCatalog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.CHAKRA))
                .thenReturn(chakraColumnConfigs());
    }

    private List<MasterdataColumnConfig> chakraColumnConfigs() {
        return List.of(
                columnConfig("Code", "code", true),
                columnConfig("Name", "name", true),
                columnConfig("Category", "category", false),
                columnConfig("Location", "location", false)
        );
    }

    private MasterdataColumnConfig columnConfig(String columnName, String targetField, boolean required) {
        return MasterdataColumnConfig.builder()
                .masterdataType(MasterdataType.CHAKRA)
                .columnName(columnName)
                .targetField(targetField)
                .required(required)
                .dataType(ColumnDataType.STRING)
                .systemDefined(true)
                .build();
    }

    private MockMultipartFile buildXlsx(List<String[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Chakras");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS[i]);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] values = rows.get(r);
                for (int c = 0; c < values.length; c++) {
                    if (values[c] != null) {
                        row.createCell(c).setCellValue(values[c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "chakras.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void kind_returnsChakra() {
        assertEquals(MasterdataType.CHAKRA, importService.kind());
    }

    @Test
    void importFile_xlsxHappyPath_createsNewChakra() throws IOException {
        when(chakraRepo.findByCode("CROWN")).thenReturn(Optional.empty());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"crown", "Crown Chakra", "major", "Top of head"}
        ));

        ImportResult result = importService.importFile(file);

        assertEquals(1, result.totalRows());
        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(chakraRepo).save(argThat(c ->
                c.getCode().equals("CROWN")
                        && c.getName().equals("Crown Chakra")
                        && c.getCategory().equals("major")
                        && c.getLocation().equals("Top of head")
                        && c.getHealerId() == null
        ));
    }

    @Test
    void importFile_existingCode_updatesInsteadOfCreating() throws IOException {
        ChakraCatalog existing = new ChakraCatalog();
        existing.setCode("CROWN");
        existing.setName("Old Name");
        when(chakraRepo.findByCode("CROWN")).thenReturn(Optional.of(existing));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"CROWN", "Crown Chakra", "major", "Top of head"}
        ));

        ImportResult result = importService.importFile(file);

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        verify(chakraRepo).save(existing);
        assertEquals("Crown Chakra", existing.getName());
    }

    @Test
    void importFile_missingCode_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"", "Crown Chakra", "major", "Top of head"}
        ));

        ImportResult result = importService.importFile(file);

        assertEquals(1, result.skipped());
        assertEquals(0, result.created());
        assertTrue(result.errors().get(0).contains("missing Code"));
        verify(chakraRepo, never()).save(any());
    }

    @Test
    void importFile_missingName_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"CROWN", "", "major", "Top of head"}
        ));

        ImportResult result = importService.importFile(file);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("missing Name"));
    }

    @Test
    void importFile_optionalColumnsAbsent_savesWithNullValues() throws IOException {
        when(chakraRepo.findByCode("AJNA")).thenReturn(Optional.empty());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"AJNA", "Ajna Chakra", null, null}
        ));

        ImportResult result = importService.importFile(file);

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(chakraRepo).save(argThat(c ->
                c.getCode().equals("AJNA")
                        && c.getName().equals("Ajna Chakra")
                        && c.getCategory() == null
                        && c.getLocation() == null
        ));
    }

    @Test
    void importFile_unrecognizedTargetField_throwsIllegalStateException() throws IOException {
        when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.CHAKRA))
                .thenReturn(List.of(columnConfig("Bogus Column", "bogusField", true)));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"CROWN", "Crown Chakra", "major", "Top of head"}
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importFile(file));
        assertTrue(ex.getMessage().contains("bogusField"));
        assertTrue(ex.getMessage().contains("CHAKRA"));
        verifyNoInteractions(chakraRepo);
    }

    @Test
    void importFile_oneBadRowDoesNotAbortOthers() throws IOException {
        when(chakraRepo.findByCode("AJNA")).thenReturn(Optional.empty());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"", "Bad Row", "major", "Nowhere"},
                new String[]{"AJNA", "Ajna Chakra", "major", "Between eyebrows"}
        ));

        ImportResult result = importService.importFile(file);

        assertEquals(2, result.totalRows());
        assertEquals(1, result.created());
        assertEquals(1, result.skipped());
    }
}
