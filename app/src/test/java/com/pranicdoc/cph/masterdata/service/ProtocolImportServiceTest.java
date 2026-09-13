package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.ProtocolChakraColorEnricher;
import com.pranicdoc.cph.masterdata.importer.SpreadsheetReader;
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
    @Spy private SpreadsheetReader spreadsheetReader = new SpreadsheetReader();
    @Mock private ProtocolChakraColorEnricher chakraColorEnricher;

    @InjectMocks private ProtocolImportService importService;

    private static final String[] HEADERS = {
            "Protocol/Technique Name", "Book", "Page", "Category", "Description", "Chapter", "Type"
    };

    private static final String[] CHAKRA_COLOR_HEADERS = {
            "Protocol name", "Protocol page number", "Chakras used", "Cleansing colour", "Energizing colour"
    };

    private static final String[] COMBINED_HEADERS = {
            "Protocol/Technique Name", "Book", "Page", "Category", "Description", "Chapter", "Type",
            "Chakras used", "Cleansing colour", "Energizing colour"
    };

    @BeforeEach
    void setUp() {
        lenient().when(protocolRepo.save(any(ProtocolCatalog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private MockMultipartFile buildXlsx(List<String[]> rows) throws IOException {
        return buildXlsx(HEADERS, rows);
    }

    private MockMultipartFile buildXlsx(String[] headers, List<String[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Protocols");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
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

    // ═══════════════════════════════════════════════════════════════════
    // MDIMPORT-22 — chakra/color enrichment (decoupled and combined shapes)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void importFile_chakraColorOnly_enrichesExistingProtocol_groupedMultiRow() throws IOException {
        when(protocolRepo.existsByNameIgnoreCase("Fever")).thenReturn(true);
        when(chakraColorEnricher.enrich(eq("Fever"), eq("Solar Plexus Chakra"), any(), any(), eq(true), isNull(), any()))
                .thenReturn(new ProtocolChakraColorEnricher.EnrichmentResult(true, false, false, null));
        when(chakraColorEnricher.enrich(eq("Fever"), eq("Back Heart Chakra"), any(), any(), eq(true), isNull(), any()))
                .thenReturn(new ProtocolChakraColorEnricher.EnrichmentResult(true, false, false, null));

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Fever", "145", "Solar Plexus Chakra", "Light Whitish Green", "Light Whitish Blue"},
                new String[]{null, null, "Back Heart Chakra", "Light Whitish Green", "Ordinary Light Whitish Violet"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(2, result.totalRows());
        assertEquals(2, result.created());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(chakraColorEnricher).enrich(eq("Fever"), eq("Solar Plexus Chakra"),
                eq(List.of("Light Whitish Green")), eq(List.of("Light Whitish Blue")), eq(true), isNull(), any());
        verify(chakraColorEnricher).enrich(eq("Fever"), eq("Back Heart Chakra"),
                eq(List.of("Light Whitish Green")), eq(List.of("Ordinary Light Whitish Violet")), eq(true), isNull(), any());
    }

    @Test
    void importFile_chakraColorOnly_splitsPlusJoinedMultipleColors() throws IOException {
        when(protocolRepo.existsByNameIgnoreCase("Headache")).thenReturn(true);
        when(chakraColorEnricher.enrich(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new ProtocolChakraColorEnricher.EnrichmentResult(true, false, false, null));

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Headache", "122", "Crown Chakra", "Light Whitish Green + Light Whitish Orange", "Light Whitish Blue"}
        ));

        importService.importFile(file, true, null);

        verify(chakraColorEnricher).enrich(eq("Headache"), eq("Crown Chakra"),
                eq(List.of("Light Whitish Green", "Light Whitish Orange")), eq(List.of("Light Whitish Blue")),
                eq(true), isNull(), any());
    }

    @Test
    void importFile_chakraColorOnly_unknownProtocol_skipsWithError_createsNothing() throws IOException {
        when(protocolRepo.existsByNameIgnoreCase("Nonexistent Protocol")).thenReturn(false);

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Nonexistent Protocol", "999", "Crown Chakra", "Light Whitish Green", "Light Whitish Blue"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertEquals(0, result.created());
        assertTrue(result.errors().get(0).contains("Nonexistent Protocol"));
        assertTrue(result.errors().get(0).contains("not found"));
        verifyNoInteractions(chakraColorEnricher);
    }

    @Test
    void importFile_combinedColumns_upsertsProtocolAndEnrichesChakraColor() throws IOException {
        when(protocolRepo.findByNameAndSourceBookAndPageNumber("Insomnia", "Advanced Pranic Healing", 45))
                .thenReturn(Optional.empty());
        when(chakraColorEnricher.enrich(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new ProtocolChakraColorEnricher.EnrichmentResult(true, false, false, null));

        MockMultipartFile file = buildXlsx(COMBINED_HEADERS, List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "Sleep protocol",
                        "Sleep Disorders", "PROTOCOL", "Crown Chakra", "Light Whitish Green", "Light Whitish Blue"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        verify(protocolRepo).save(argThat(p -> p.getName().equals("Insomnia")));
        verify(chakraColorEnricher).enrich(eq("Insomnia"), eq("Crown Chakra"),
                eq(List.of("Light Whitish Green")), eq(List.of("Light Whitish Blue")), eq(true), isNull(), any());
        assertEquals(2, result.created()); // 1 protocol + 1 chakra/color usage
        assertEquals(0, result.skipped());
    }
}
