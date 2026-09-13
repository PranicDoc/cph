package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.SpreadsheetReader;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolImportServiceTest {

    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private MasterdataColumnConfigRepository columnConfigRepo;
    @Spy private SpreadsheetReader spreadsheetReader = new SpreadsheetReader();

    @InjectMocks private ProtocolImportService importService;

    private static final String[] HEADERS = {
            "Protocol/Technique Name", "Book", "Page", "Category", "Description", "Chapter", "Type"
    };

    @BeforeEach
    void setUp() {
        lenient().when(protocolRepo.save(any(ProtocolCatalog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL))
                .thenReturn(protocolColumnConfigs());
    }

    private List<MasterdataColumnConfig> protocolColumnConfigs() {
        return List.of(
                columnConfig("Protocol/Technique Name", "name", true, ColumnDataType.STRING),
                columnConfig("Book", "sourceBook", true, ColumnDataType.STRING),
                columnConfig("Page", "pageNumber", true, ColumnDataType.NUMBER),
                columnConfig("Category", "category", false, ColumnDataType.STRING),
                columnConfig("Description", "description", false, ColumnDataType.STRING),
                columnConfig("Chapter", "chapter", false, ColumnDataType.STRING),
                columnConfig("Type", "protocolType", true, ColumnDataType.ENUM)
        );
    }

    private MasterdataColumnConfig columnConfig(String columnName, String targetField, boolean required, ColumnDataType dataType) {
        return MasterdataColumnConfig.builder()
                .masterdataType(MasterdataType.PROTOCOL)
                .columnName(columnName)
                .targetField(targetField)
                .required(required)
                .dataType(dataType)
                .systemDefined(true)
                .build();
    }

    private MockMultipartFile buildXlsx(List<String[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Protocols");
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
            return new MockMultipartFile("file", "protocols.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private MockMultipartFile buildCsv(List<String[]> rows) {
        StringBuilder sb = new StringBuilder(String.join(",", HEADERS)).append("\n");
        for (String[] values : rows) {
            sb.append(String.join(",", values)).append("\n");
        }
        return new MockMultipartFile("file", "protocols.csv", "text/csv",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importFile_xlsxHappyPath_createsNewProtocol() throws IOException {
        when(protocolRepo.findByNameAndSourceBookAndPageNumber("Insomnia", "Advanced Pranic Healing", 45))
                .thenReturn(Optional.empty());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "Sleep protocol", "Sleep Disorders", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.totalRows());
        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(protocolRepo).save(argThat(p ->
                p.getName().equals("Insomnia")
                        && p.getSourceBook().equals("Advanced Pranic Healing")
                        && p.getPageNumber() == 45
                        && p.getProtocolType() == ProtocolType.PROTOCOL
                        && p.getHealerId() == null
        ));
    }

    @Test
    void importFile_csvHappyPath_createsNewProtocol() {
        when(protocolRepo.findByNameAndSourceBookAndPageNumber("Fever", "Pranic Psychotherapy", 12))
                .thenReturn(Optional.empty());

        MockMultipartFile file = buildCsv(List.<String[]>of(
                new String[]{"Fever", "Pranic Psychotherapy", "12", "Physical Ailment", "Fever protocol", "Common Ailments", "SUPPLEMENTARY"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        verify(protocolRepo).save(argThat(p -> p.getProtocolType() == ProtocolType.SUPPLEMENTARY));
    }

    @Test
    void importFile_existingRow_updatesInsteadOfCreating() throws IOException {
        ProtocolCatalog existing = new ProtocolCatalog();
        existing.setName("Insomnia");
        existing.setSourceBook("Advanced Pranic Healing");
        existing.setPageNumber(45);
        existing.setProtocolType(ProtocolType.PROTOCOL);
        when(protocolRepo.findByNameAndSourceBookAndPageNumber("Insomnia", "Advanced Pranic Healing", 45))
                .thenReturn(Optional.of(existing));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "Updated desc", "Sleep Disorders", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        verify(protocolRepo).save(existing);
        assertEquals("Updated desc", existing.getDescription());
    }

    @Test
    void importFile_nonSuperAdmin_cannotOverwriteSystemRow() throws IOException {
        ProtocolCatalog existing = new ProtocolCatalog();
        existing.setName("Insomnia");
        existing.setSourceBook("Advanced Pranic Healing");
        existing.setPageNumber(45);
        existing.setProtocolType(ProtocolType.PROTOCOL);
        existing.setHealerId(null); // SYSTEM row
        when(protocolRepo.findByNameAndSourceBookAndPageNumber("Insomnia", "Advanced Pranic Healing", 45))
                .thenReturn(Optional.of(existing));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "Hostile update", "Sleep Disorders", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, false, UUID.randomUUID());

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("owned by another party"));
        assertNull(existing.getDescription());
        verify(protocolRepo, never()).save(any());
    }

    @Test
    void importFile_nonSuperAdmin_cannotOverwriteAnotherHealersCustomRow() throws IOException {
        ProtocolCatalog existing = new ProtocolCatalog();
        existing.setName("Insomnia");
        existing.setSourceBook("Advanced Pranic Healing");
        existing.setPageNumber(45);
        existing.setProtocolType(ProtocolType.PROTOCOL);
        existing.setHealerId(UUID.randomUUID()); // owned by a DIFFERENT healer
        when(protocolRepo.findByNameAndSourceBookAndPageNumber("Insomnia", "Advanced Pranic Healing", 45))
                .thenReturn(Optional.of(existing));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "Hostile update", "Sleep Disorders", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, false, UUID.randomUUID());

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("owned by another party"));
        assertNull(existing.getDescription());
        verify(protocolRepo, never()).save(any());
    }

    @Test
    void importFile_nonSuperAdmin_newRow_stampsCallerHealerId() throws IOException {
        UUID callerHealerId = UUID.randomUUID();
        when(protocolRepo.findByNameAndSourceBookAndPageNumber("Fever", "Pranic Psychotherapy", 12))
                .thenReturn(Optional.empty());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Fever", "Pranic Psychotherapy", "12", "Physical Ailment", "Fever protocol", "Common Ailments", "SUPPLEMENTARY"}
        ));

        ImportResult result = importService.importFile(file, false, callerHealerId);

        assertEquals(1, result.created());
        verify(protocolRepo).save(argThat(p -> callerHealerId.equals(p.getHealerId())));
    }

    @Test
    void importFile_missingName_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertEquals(0, result.created());
        assertTrue(result.errors().get(0).contains("Protocol/Technique Name"));
        verify(protocolRepo, never()).save(any());
    }

    @Test
    void importFile_missingBook_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("Book"));
    }

    @Test
    void importFile_missingPage_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("Page"));
    }

    @Test
    void importFile_nonNumericPage_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "N/A", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("not numeric"));
    }

    @Test
    void importFile_invalidType_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "Supplemental"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("Type"));
    }

    @Test
    void importFile_oneBadRowDoesNotAbortOthers() throws IOException {
        when(protocolRepo.findByNameAndSourceBookAndPageNumber(eq("Fever"), any(), any()))
                .thenReturn(Optional.empty());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL"},
                new String[]{"Fever", "Advanced Pranic Healing", "10", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(2, result.totalRows());
        assertEquals(1, result.created());
        assertEquals(1, result.skipped());
    }

    @Test
    void importFile_unsupportedExtension_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "protocols.txt", "text/plain", "junk".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> importService.importFile(file, true, null));
        assertTrue(ex.getMessage().contains("Unsupported file type"));
        verifyNoInteractions(protocolRepo);
    }

    @Test
    void importFile_missingRequiredColumnConfig_throwsIllegalStateException() throws IOException {
        // "Book" (sourceBook) config row deleted entirely (WR-02) — should fail fast up
        // front rather than silently skipping the required-ness check for that field.
        when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL))
                .thenReturn(List.of(
                        columnConfig("Protocol/Technique Name", "name", true, ColumnDataType.STRING),
                        columnConfig("Page", "pageNumber", true, ColumnDataType.NUMBER),
                        columnConfig("Type", "protocolType", true, ColumnDataType.ENUM)
                ));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importFile(file, true, null));
        assertTrue(ex.getMessage().contains("sourceBook"));
        assertTrue(ex.getMessage().contains("PROTOCOL"));
        verifyNoInteractions(protocolRepo);
    }

    @Test
    void importFile_unrecognizedTargetField_throwsIllegalStateException() throws IOException {
        when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL))
                .thenReturn(List.of(columnConfig("Bogus Column", "bogusField", true, ColumnDataType.STRING)));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importFile(file, true, null));
        assertTrue(ex.getMessage().contains("bogusField"));
        assertTrue(ex.getMessage().contains("PROTOCOL"));
        verifyNoInteractions(protocolRepo);
    }
}
