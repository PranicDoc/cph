package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportResult;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolImportServiceTest {

    @Mock private ProtocolCatalogRepository protocolRepo;

    @InjectMocks private ProtocolImportService importService;

    private static final String[] HEADERS = {
            "Protocol/Technique Name", "Book", "Page", "Category", "Description", "Chapter", "Type"
    };

    @BeforeEach
    void setUp() {
        lenient().when(protocolRepo.save(any(ProtocolCatalog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
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

        ProtocolImportResult result = importService.importFile(file);

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

        ProtocolImportResult result = importService.importFile(file);

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

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        verify(protocolRepo).save(existing);
        assertEquals("Updated desc", existing.getDescription());
    }

    @Test
    void importFile_missingName_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ProtocolImportResult result = importService.importFile(file);

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

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("Book"));
    }

    @Test
    void importFile_missingPage_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("Page"));
    }

    @Test
    void importFile_nonNumericPage_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "N/A", "Physical Ailment", "desc", "Chapter", "PROTOCOL"}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("not numeric"));
    }

    @Test
    void importFile_invalidType_skipsRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "Supplemental"}
        ));

        ProtocolImportResult result = importService.importFile(file);

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

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(2, result.totalRows());
        assertEquals(1, result.created());
        assertEquals(1, result.skipped());
    }

    @Test
    void importFile_unsupportedExtension_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "protocols.txt", "text/plain", "junk".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> importService.importFile(file));
        assertTrue(ex.getMessage().contains("Unsupported file type"));
        verifyNoInteractions(protocolRepo);
    }
}
