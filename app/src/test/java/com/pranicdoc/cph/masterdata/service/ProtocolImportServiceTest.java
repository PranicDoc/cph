package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ProtocolImportResult;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakra;
import com.pranicdoc.cph.masterdata.model.ProtocolChakraColor;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraColorRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolChakraRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolImportServiceTest {

    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private ChakraCatalogRepository chakraRepo;
    @Mock private PranaColorCatalogRepository pranaColorRepo;
    @Mock private ProtocolChakraRepository protocolChakraRepo;
    @Mock private ProtocolChakraColorRepository protocolChakraColorRepo;

    @InjectMocks private ProtocolImportService importService;

    private static final String[] HEADERS = {
            "Protocol/Technique Name", "Book", "Page", "Category", "Description", "Chapter", "Type"
    };

    private static final String[] CHAKRA_COLOR_HEADERS = {
            "Protocol/Technique Name", "Book", "Page", "Category", "Description", "Chapter", "Type",
            "Chakras Used", "Cleansing Color", "Energizing Color"
    };

    @BeforeEach
    void setUp() {
        lenient().when(protocolRepo.save(any(ProtocolCatalog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(chakraRepo.findByHealerIdIsNull()).thenReturn(List.of());
        lenient().when(pranaColorRepo.findByHealerIdIsNull()).thenReturn(List.of());
        lenient().when(protocolChakraRepo.findByProtocol_IdIn(any())).thenReturn(List.of());
        lenient().when(protocolChakraColorRepo.findByProtocolChakra_IdIn(any())).thenReturn(List.of());
        lenient().when(chakraRepo.save(any(ChakraCatalog.class))).thenAnswer(inv -> {
            ChakraCatalog c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        lenient().when(pranaColorRepo.save(any(PranaColorCatalog.class))).thenAnswer(inv -> {
            PranaColorCatalog c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        lenient().when(protocolChakraRepo.save(any(ProtocolChakra.class))).thenAnswer(inv -> {
            ProtocolChakra pc = inv.getArgument(0);
            if (pc.getId() == null) pc.setId(UUID.randomUUID());
            return pc;
        });
        lenient().when(protocolChakraColorRepo.save(any(ProtocolChakraColor.class))).thenAnswer(inv -> {
            ProtocolChakraColor pcc = inv.getArgument(0);
            if (pcc.getId() == null) pcc.setId(UUID.randomUUID());
            return pcc;
        });
    }

    /** Builds an xlsx with the given headers/rows — used by both the base and chakra/color test groups. */
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

    private MockMultipartFile buildXlsx(List<String[]> rows) throws IOException {
        return buildXlsx(HEADERS, rows);
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

    // ── Chakra/color enrichment: relationship-only, never creates/modifies protocol rows ──

    private ProtocolCatalog existingProtocol(String name, String book, int page) {
        ProtocolCatalog p = new ProtocolCatalog();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setSourceBook(book);
        p.setPageNumber(page);
        p.setProtocolType(ProtocolType.PROTOCOL);
        return p;
    }

    /**
     * The requirement's own worked example: Fever addresses 3 chakras, each with
     * its own cleansing/energizing color set (Solar Plexus and Back Heart both
     * list "Light Whitish Green" under both usage types — same color, two rows).
     * Verifies the grouped continuation-row format normalizes into exactly the
     * right number of ProtocolChakra / ProtocolChakraColor rows, with every
     * chakra and color backfilled by name (none pre-exist in the catalog) — and
     * that the already-existing Fever protocol itself is never written.
     */
    @Test
    void importFile_feverExample_normalizesIntoProtocolChakraAndColorLinks() throws IOException {
        ProtocolCatalog fever = existingProtocol("Fever", "Advanced Pranic Healing", 12);
        when(protocolRepo.findByNameAndSourceBook("Fever", "Advanced Pranic Healing"))
                .thenReturn(List.of(fever));

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Fever", "Advanced Pranic Healing", "12", "Physical Ailment", "desc", "Chapter", "PROTOCOL",
                        "Solar Plexus Chakra", "Light Whitish Green",
                        "Light Whitish Green, Light Whitish Blue, Light Whitish Orange"},
                new String[]{null, null, null, null, null, null, null,
                        "Back Heart Chakra", "Light Whitish Green",
                        "Light Whitish Green, Ordinary Light Whitish Violet"},
                new String[]{null, null, null, null, null, null, null,
                        "Spleen Chakra", "Light Whitish Violet", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(3, result.totalRows());
        assertTrue(result.errors().isEmpty(), "unexpected errors: " + result.errors());
        assertEquals(0, result.skipped());
        assertEquals(0, result.updated());
        // 3 chakra links + (4 + 3 + 1) color links = 11 — the protocol itself is never written
        assertEquals(3 + 8, result.created());
        verify(protocolRepo, never()).save(any());

        verify(protocolChakraRepo, times(3)).save(any(ProtocolChakra.class));
        verify(protocolChakraColorRepo, times(8)).save(any(ProtocolChakraColor.class));
        // 3 distinct chakra names backfilled once each
        verify(chakraRepo, times(3)).save(any(ChakraCatalog.class));
        // 5 distinct color names backfilled once each, despite appearing in multiple cells
        verify(pranaColorRepo, times(5)).save(any(PranaColorCatalog.class));

        ArgumentCaptor<ProtocolChakraColor> colorLinkCaptor = ArgumentCaptor.forClass(ProtocolChakraColor.class);
        verify(protocolChakraColorRepo, times(8)).save(colorLinkCaptor.capture());
        long cleansingCount = colorLinkCaptor.getAllValues().stream()
                .filter(l -> l.getUsageType() == ColorUsageType.CLEANSING).count();
        long energizingCount = colorLinkCaptor.getAllValues().stream()
                .filter(l -> l.getUsageType() == ColorUsageType.ENERGISING).count();
        assertEquals(3, cleansingCount); // one cleansing color per chakra
        assertEquals(5, energizingCount); // 3 (solar plexus) + 2 (back heart) + 0 (spleen)

        // A backfilled color takes the category it was first imported under.
        ArgumentCaptor<PranaColorCatalog> colorCaptor = ArgumentCaptor.forClass(PranaColorCatalog.class);
        verify(pranaColorRepo, times(5)).save(colorCaptor.capture());
        Map<String, Set<ColorUsageType>> categoriesByName = colorCaptor.getAllValues().stream()
                .collect(Collectors.toMap(PranaColorCatalog::getName, PranaColorCatalog::getCategories));
        assertEquals(Set.of(ColorUsageType.ENERGISING), categoriesByName.get("Light Whitish Blue"));
        assertEquals(Set.of(ColorUsageType.CLEANSING), categoriesByName.get("Light Whitish Violet"));
    }

    @Test
    void importFile_chakraColorEnrichment_protocolNotFound_skipsGroupWithoutCreatingIt() throws IOException {
        when(protocolRepo.findByNameAndSourceBook("Fever", "Advanced Pranic Healing"))
                .thenReturn(List.of());

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Fever", "Advanced Pranic Healing", "12", "Physical Ailment", "desc", "Chapter", "PROTOCOL",
                        "Solar Plexus Chakra", "Light Whitish Green", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("not found"));
        assertTrue(result.errors().get(0).contains("never creates or modifies protocols"));
        verify(protocolRepo, never()).save(any());
        verifyNoInteractions(protocolChakraRepo);
        verifyNoInteractions(protocolChakraColorRepo);
        verify(chakraRepo, never()).save(any());
        verify(pranaColorRepo, never()).save(any());
    }

    @Test
    void importFile_chakraColorEnrichment_reusesExistingChakraAndColorByName_noDuplicateBackfill() throws IOException {
        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(UUID.randomUUID());
        existingChakra.setCode("CROWN");
        existingChakra.setName("Crown Chakra");
        when(chakraRepo.findByHealerIdIsNull()).thenReturn(List.of(existingChakra));

        PranaColorCatalog existingColor = new PranaColorCatalog();
        existingColor.setId(UUID.randomUUID());
        existingColor.setCode("GREEN");
        existingColor.setName("Green Prana");
        when(pranaColorRepo.findByHealerIdIsNull()).thenReturn(List.of(existingColor));

        ProtocolCatalog headache = existingProtocol("Headache", "Advanced Pranic Healing", 20);
        when(protocolRepo.findByNameAndSourceBook("Headache", "Advanced Pranic Healing"))
                .thenReturn(List.of(headache));

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Headache", "Advanced Pranic Healing", "20", "Physical Ailment", "desc", "Chapter", "PROTOCOL",
                        "Crown Chakra", "Green Prana", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertTrue(result.errors().isEmpty(), "unexpected errors: " + result.errors());
        verify(protocolRepo, never()).save(any());
        verify(chakraRepo, never()).save(any());
        verify(pranaColorRepo, never()).save(any());

        ArgumentCaptor<ProtocolChakra> linkCaptor = ArgumentCaptor.forClass(ProtocolChakra.class);
        verify(protocolChakraRepo).save(linkCaptor.capture());
        assertEquals(existingChakra.getId(), linkCaptor.getValue().getChakra().getId());

        ArgumentCaptor<ProtocolChakraColor> colorLinkCaptor = ArgumentCaptor.forClass(ProtocolChakraColor.class);
        verify(protocolChakraColorRepo).save(colorLinkCaptor.capture());
        assertEquals(existingColor.getId(), colorLinkCaptor.getValue().getColor().getId());
    }

    @Test
    void importFile_chakraColorEnrichment_backfillsUnknownChakraAndColor_asSystemRows() throws IOException {
        ProtocolCatalog insomnia = existingProtocol("Insomnia", "Advanced Pranic Healing", 45);
        when(protocolRepo.findByNameAndSourceBook("Insomnia", "Advanced Pranic Healing"))
                .thenReturn(List.of(insomnia));

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL",
                        "Ajna Chakra", "Light Whitish Blue", null}
        ));

        importService.importFile(file);

        verify(protocolRepo, never()).save(any());

        ArgumentCaptor<ChakraCatalog> chakraCaptor = ArgumentCaptor.forClass(ChakraCatalog.class);
        verify(chakraRepo).save(chakraCaptor.capture());
        assertEquals("Ajna Chakra", chakraCaptor.getValue().getName());
        assertEquals("AJNA_CHAKRA", chakraCaptor.getValue().getCode());
        assertNull(chakraCaptor.getValue().getHealerId());

        ArgumentCaptor<PranaColorCatalog> colorCaptor = ArgumentCaptor.forClass(PranaColorCatalog.class);
        verify(pranaColorRepo).save(colorCaptor.capture());
        assertEquals("Light Whitish Blue", colorCaptor.getValue().getName());
        assertEquals("LIGHT_WHITISH_BLUE", colorCaptor.getValue().getCode());
        assertNull(colorCaptor.getValue().getHealerId());
    }

    @Test
    void importFile_chakraColorEnrichment_reimport_doesNotCreateDuplicateLinks() throws IOException {
        ProtocolCatalog existingProtocol = existingProtocol("Insomnia", "Advanced Pranic Healing", 45);
        when(protocolRepo.findByNameAndSourceBook("Insomnia", "Advanced Pranic Healing"))
                .thenReturn(List.of(existingProtocol));

        ChakraCatalog existingChakra = new ChakraCatalog();
        existingChakra.setId(UUID.randomUUID());
        existingChakra.setCode("AJNA");
        existingChakra.setName("Ajna Chakra");
        when(chakraRepo.findByHealerIdIsNull()).thenReturn(List.of(existingChakra));

        PranaColorCatalog existingColor = new PranaColorCatalog();
        existingColor.setId(UUID.randomUUID());
        existingColor.setCode("LWB");
        existingColor.setName("Light Whitish Blue");
        when(pranaColorRepo.findByHealerIdIsNull()).thenReturn(List.of(existingColor));

        ProtocolChakra existingLink = ProtocolChakra.builder().protocol(existingProtocol).chakra(existingChakra).build();
        existingLink.setId(UUID.randomUUID());
        when(protocolChakraRepo.findByProtocol_IdIn(List.of(existingProtocol.getId()))).thenReturn(List.of(existingLink));

        ProtocolChakraColor existingColorLink = ProtocolChakraColor.builder()
                .protocolChakra(existingLink).color(existingColor).usageType(ColorUsageType.CLEANSING).build();
        when(protocolChakraColorRepo.findByProtocolChakra_IdIn(List.of(existingLink.getId())))
                .thenReturn(List.of(existingColorLink));

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Insomnia", "Advanced Pranic Healing", "45", "Physical Ailment", "desc", "Chapter", "PROTOCOL",
                        "Ajna Chakra", "Light Whitish Blue", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        // Everything is already linked, and the protocol row is never touched by this path.
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.skipped());
        verify(protocolRepo, never()).save(any());
        verify(protocolChakraRepo, never()).save(any());
        verify(protocolChakraColorRepo, never()).save(any());
        verify(chakraRepo, never()).save(any());
        verify(pranaColorRepo, never()).save(any());
    }

    @Test
    void importFile_chakraColorEnrichment_colorDataWithoutChakraName_skipsRowWithError() throws IOException {
        ProtocolCatalog fever = existingProtocol("Fever", "Advanced Pranic Healing", 12);
        when(protocolRepo.findByNameAndSourceBook("Fever", "Advanced Pranic Healing"))
                .thenReturn(List.of(fever));

        MockMultipartFile file = buildXlsx(CHAKRA_COLOR_HEADERS, List.<String[]>of(
                new String[]{"Fever", "Advanced Pranic Healing", "12", "Physical Ailment", "desc", "Chapter", "PROTOCOL",
                        null, "Light Whitish Green", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("Chakras Used"));
        assertEquals(0, result.created());
        verify(protocolRepo, never()).save(any());
        verify(protocolChakraRepo, never()).save(any());
        verifyNoInteractions(protocolChakraColorRepo);
    }

    // ── Minimal enrichment-only file (name/page/chakra/color columns only, no Book) ──

    private static final String[] MINIMAL_HEADERS = {
            "Protocol/Technique Name", "Page", "Chakras Used", "Cleansing Color", "Energizing Color"
    };

    @Test
    void importFile_noBookColumn_defaultsSourceBookForLookupOnly() throws IOException {
        ProtocolCatalog fever = existingProtocol("Fever", ProtocolImportService.DEFAULT_SOURCE_BOOK, 145);
        when(protocolRepo.findByNameAndSourceBook("Fever", ProtocolImportService.DEFAULT_SOURCE_BOOK))
                .thenReturn(List.of(fever));

        MockMultipartFile file = buildXlsx(MINIMAL_HEADERS, List.<String[]>of(
                new String[]{"Fever", "145", "Solar Plexus Chakra", "Light Whitish Green",
                        "Light Whitish Green, Light Whitish Blue"}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertTrue(result.errors().isEmpty(), "unexpected errors: " + result.errors());
        verify(protocolRepo, never()).save(any()); // lookup only, never written
        // 1 chakra link + 3 color links (1 cleansing green, 2 energizing: green + blue —
        // green appears under both usage types, which is two distinct rows, not a duplicate)
        assertEquals(1 + 3, result.created());
    }

    @Test
    void importFile_pageIgnored_matchesByNameAndBookAlone() throws IOException {
        ProtocolCatalog fever = existingProtocol("Fever", ProtocolImportService.DEFAULT_SOURCE_BOOK, 145);
        when(protocolRepo.findByNameAndSourceBook("Fever", ProtocolImportService.DEFAULT_SOURCE_BOOK))
                .thenReturn(List.of(fever));

        // Page column present but blank — matching no longer depends on it at all.
        MockMultipartFile file = buildXlsx(MINIMAL_HEADERS, List.<String[]>of(
                new String[]{"Fever", null, "Solar Plexus Chakra", "Light Whitish Green", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertTrue(result.errors().isEmpty(), "unexpected errors: " + result.errors());
        assertEquals(0, result.skipped());
        verify(protocolRepo, never()).save(any());
        verify(protocolChakraRepo).save(argThat(l -> l.getProtocol().getId().equals(fever.getId())));
    }

    @Test
    void importFile_nameMatchesMultipleBooksSamePages_enrichesEveryMatch() throws IOException {
        ProtocolCatalog page122 = existingProtocol("Migraine Headache", "Advanced Pranic Healing", 122);
        ProtocolCatalog page123 = existingProtocol("Migraine Headache", "Advanced Pranic Healing", 123);
        when(protocolRepo.findByNameAndSourceBook("Migraine Headache", "Advanced Pranic Healing"))
                .thenReturn(List.of(page122, page123));

        MockMultipartFile file = buildXlsx(MINIMAL_HEADERS, List.<String[]>of(
                new String[]{"Migraine Headache", null, "Ajna Chakra", "Light Whitish Green", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertTrue(result.errors().isEmpty(), "unexpected errors: " + result.errors());
        verify(protocolRepo, never()).save(any());
        // one ProtocolChakra link created per matching protocol row
        verify(protocolChakraRepo).save(argThat(l -> l.getProtocol().getId().equals(page122.getId())));
        verify(protocolChakraRepo).save(argThat(l -> l.getProtocol().getId().equals(page123.getId())));
        verify(protocolChakraRepo, times(2)).save(any(ProtocolChakra.class));
    }

    @Test
    void importFile_placeholderChakraPhrase_rejectedNotBackfilled() throws IOException {
        ProtocolCatalog treatingInfants = existingProtocol("Treating Infants", ProtocolImportService.DEFAULT_SOURCE_BOOK, 121);
        when(protocolRepo.findByNameAndSourceBook("Treating Infants", ProtocolImportService.DEFAULT_SOURCE_BOOK))
                .thenReturn(List.of(treatingInfants));

        MockMultipartFile file = buildXlsx(MINIMAL_HEADERS, List.<String[]>of(
                new String[]{"Treating Infants", "121", "Not Specified", "Not Specified", "White"}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(1, result.skipped()); // the invalid "Not Specified" chakra name
        assertTrue(result.errors().get(0).contains("does not look like a chakra name"));
        assertEquals(0, result.created());
        verify(protocolRepo, never()).save(any());
        verify(chakraRepo, never()).save(any());
        verify(protocolChakraRepo, never()).save(any());
    }

    @Test
    void importFile_proseWithEmbeddedColonInColorCell_rejectedNotBackfilled() throws IOException {
        ProtocolCatalog migraine = existingProtocol("Migraine Headache", ProtocolImportService.DEFAULT_SOURCE_BOOK, 122);
        when(protocolRepo.findByNameAndSourceBook("Migraine Headache", ProtocolImportService.DEFAULT_SOURCE_BOOK))
                .thenReturn(List.of(migraine));

        MockMultipartFile file = buildXlsx(MINIMAL_HEADERS, List.<String[]>of(
                new String[]{"Migraine Headache", "122", "Ajna Chakra",
                        "Light whitish green",
                        "Light whitish green: Solar Plexus, Back Heart, Ajna, Forehead, Crown & Back Head"}
        ));

        ProtocolImportResult result = importService.importFile(file);

        // The energizing cell embeds a colon-separated per-chakra breakdown (real messy source data) —
        // rejected rather than backfilled as a literal (and wrong) color name. The plain cleansing
        // phrase alongside it is unaffected and still gets linked normally.
        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("energizing color"));
        verify(pranaColorRepo, times(1)).save(any()); // only "Light whitish green" backfilled
        verify(protocolChakraColorRepo, times(1)).save(argThat(l -> l.getUsageType() == ColorUsageType.CLEANSING));
    }

    /**
     * Known, deliberately-unaddressed limitation: a phrase with no colon/newline and under the
     * length cap (e.g. "Localised Sweeping", a technique name rather than a color) passes the
     * guard and IS backfilled as a literal color catalog row. Distinguishing "plausible color
     * name" from "plausible technique name" would need a curated color vocabulary — out of scope
     * here (see class javadoc); this test pins that this specific shape is NOT caught, so a future
     * tightening of the guard is a deliberate choice, not an accidental regression.
     */
    @Test
    void importFile_plainButNonColorPhrase_isNotCaughtByTheGuard_knownLimitation() throws IOException {
        ProtocolCatalog toothache = existingProtocol("Toothache", ProtocolImportService.DEFAULT_SOURCE_BOOK, 123);
        when(protocolRepo.findByNameAndSourceBook("Toothache", ProtocolImportService.DEFAULT_SOURCE_BOOK))
                .thenReturn(List.of(toothache));

        MockMultipartFile file = buildXlsx(MINIMAL_HEADERS, List.<String[]>of(
                new String[]{"Toothache", "123", "Affected part", "Localised Sweeping", null}
        ));

        ProtocolImportResult result = importService.importFile(file);

        assertEquals(0, result.skipped());
        verify(pranaColorRepo).save(argThat(c -> c.getName().equals("Localised Sweeping")));
    }
}
