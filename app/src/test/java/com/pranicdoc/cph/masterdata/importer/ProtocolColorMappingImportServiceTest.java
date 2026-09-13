package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolColorMappingImportServiceTest {

    @Mock private ProtocolColorMappingRepository mappingRepo;
    @Mock private PranaColorCatalogRepository pranaColorRepo;
    @Mock private ProtocolCatalogRepository protocolRepo;
    @Mock private MasterdataColumnConfigRepository columnConfigRepo;
    @Spy private SpreadsheetReader spreadsheetReader = new SpreadsheetReader();

    @InjectMocks private ProtocolColorMappingImportService importService;

    private static final String[] HEADERS = {"Protocol Name", "Color Code", "Usage Type", "Chakra Name"};

    @BeforeEach
    void setUp() {
        lenient().when(mappingRepo.save(any(ProtocolColorMapping.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL_COLOR_MAPPING))
                .thenReturn(mappingColumnConfigs());
        lenient().when(protocolRepo.existsByNameIgnoreCase(anyString())).thenReturn(true);
    }

    private List<MasterdataColumnConfig> mappingColumnConfigs() {
        return List.of(
                columnConfig("Protocol Name", "protocolName", true),
                columnConfig("Color Code", "colorId", true),
                columnConfig("Usage Type", "usageType", true),
                columnConfig("Chakra Name", "chakraName", false)
        );
    }

    private MasterdataColumnConfig columnConfig(String columnName, String targetField, boolean required) {
        return MasterdataColumnConfig.builder()
                .masterdataType(MasterdataType.PROTOCOL_COLOR_MAPPING)
                .columnName(columnName)
                .targetField(targetField)
                .required(required)
                .dataType(ColumnDataType.STRING)
                .systemDefined(true)
                .build();
    }

    private PranaColorCatalog color(UUID id, String code) {
        PranaColorCatalog c = new PranaColorCatalog();
        c.setId(id);
        c.setCode(code);
        c.setName(code);
        return c;
    }

    private MockMultipartFile buildXlsx(List<String[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("ProtocolColorMappings");
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
            return new MockMultipartFile("file", "mappings.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void importFile_happyPathCreate_newTupleCreatesMapping() throws IOException {
        UUID colorId = UUID.randomUUID();
        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(colorId, "WHITE")));
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull("Insomnia Relief")).thenReturn(List.of());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", "Crown"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.totalRows());
        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(mappingRepo).save(argThat(m ->
                m.getProtocolName().equals("Insomnia Relief")
                        && colorId.equals(m.getColorId())
                        && m.getUsageType() == ColorUsageType.CLEANSING
                        && m.getHealerId() == null
                        && "Crown".equals(m.getChakraName())
        ));
    }

    @Test
    void importFile_happyPathUpdate_existingTupleUpdatedInPlaceNotDuplicated() throws IOException {
        UUID colorId = UUID.randomUUID();
        ProtocolColorMapping existing = new ProtocolColorMapping();
        existing.setProtocolName("Insomnia Relief");
        existing.setColorId(colorId);
        existing.setUsageType(ColorUsageType.CLEANSING);
        existing.setHealerId(null);

        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(colorId, "WHITE")));
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull("Insomnia Relief")).thenReturn(List.of(existing));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", "Crown"}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        verify(mappingRepo).save(existing);
        verify(mappingRepo, never()).save(argThat(m -> m != existing));
        assertEquals("Crown", existing.getChakraName());
    }

    @Test
    void importFile_nonSuperAdmin_cannotOverwriteSystemMapping() throws IOException {
        UUID colorId = UUID.randomUUID();
        ProtocolColorMapping existing = new ProtocolColorMapping();
        existing.setProtocolName("Insomnia Relief");
        existing.setColorId(colorId);
        existing.setUsageType(ColorUsageType.CLEANSING);
        existing.setHealerId(null); // SYSTEM row

        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(colorId, "WHITE")));
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull("Insomnia Relief")).thenReturn(List.of(existing));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", "Hostile"}
        ));

        ImportResult result = importService.importFile(file, false, UUID.randomUUID());

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("owned by another party"));
        assertNull(existing.getChakraName());
        verify(mappingRepo, never()).save(any());
    }

    @Test
    void importFile_nonSuperAdmin_cannotOverwriteAnotherHealersCustomRow() throws IOException {
        UUID colorId = UUID.randomUUID();
        UUID callerHealerId = UUID.randomUUID();
        ProtocolColorMapping existing = new ProtocolColorMapping();
        existing.setProtocolName("Insomnia Relief");
        existing.setColorId(colorId);
        existing.setUsageType(ColorUsageType.CLEANSING);
        existing.setHealerId(UUID.randomUUID()); // owned by a DIFFERENT healer

        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(colorId, "WHITE")));
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull("Insomnia Relief")).thenReturn(List.of());
        // WR-01: findExisting now also looks up the caller's own healerId-scoped rows —
        // simulate that lookup surfacing a row that (unexpectedly) belongs to someone
        // else, to verify the ownership guard itself (not the query) is what denies it.
        when(mappingRepo.findByProtocolNameAndHealerId("Insomnia Relief", callerHealerId)).thenReturn(List.of(existing));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", "Hostile"}
        ));

        ImportResult result = importService.importFile(file, false, callerHealerId);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().get(0).contains("owned by another party"));
        assertNull(existing.getChakraName());
        verify(mappingRepo, never()).save(any());
    }

    @Test
    void importFile_nonSuperAdmin_newRow_stampsCallerHealerId() throws IOException {
        UUID callerHealerId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(colorId, "WHITE")));
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull("Insomnia Relief")).thenReturn(List.of());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", "Crown"}
        ));

        ImportResult result = importService.importFile(file, false, callerHealerId);

        assertEquals(1, result.created());
        verify(mappingRepo).save(argThat(m -> callerHealerId.equals(m.getHealerId())));
    }

    @Test
    void importFile_unresolvableColorCode_skipsOnlyThatRow() throws IOException {
        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(UUID.randomUUID(), "WHITE")));
        when(pranaColorRepo.findByCode("BOGUS")).thenReturn(Optional.empty());
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull(anyString())).thenReturn(List.of());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "BOGUS", "CLEANSING", null},
                new String[]{"Fever Reduction", "WHITE", "ENERGISING", null}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(2, result.totalRows());
        assertEquals(1, result.skipped());
        assertEquals(1, result.created());
        assertTrue(result.errors().get(0).contains("Color Code"));
        assertTrue(result.errors().get(0).contains("BOGUS"));
    }

    @Test
    void importFile_unresolvableProtocolName_skipsOnlyThatRow_caseInsensitiveStillResolves() throws IOException {
        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(UUID.randomUUID(), "WHITE")));
        when(protocolRepo.existsByNameIgnoreCase("Unknown Protocol")).thenReturn(false);
        when(protocolRepo.existsByNameIgnoreCase("insomnia relief")).thenReturn(true);
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull(anyString())).thenReturn(List.of());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Unknown Protocol", "WHITE", "CLEANSING", null},
                new String[]{"insomnia relief", "WHITE", "CLEANSING", null}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertEquals(1, result.created());
        assertTrue(result.errors().get(0).contains("Protocol Name"));
        assertTrue(result.errors().get(0).contains("Unknown Protocol"));
        verify(mappingRepo).save(argThat(m -> m.getProtocolName().equals("insomnia relief")));
    }

    @Test
    void importFile_invalidUsageType_skipsRowWithError() throws IOException {
        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(UUID.randomUUID(), "WHITE")));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "SPARKLING", null}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.skipped());
        assertEquals(0, result.created());
        assertTrue(result.errors().get(0).contains("Usage Type"));
        verifyNoInteractions(mappingRepo);
    }

    @Test
    void importFile_missingRequiredFields_skipEachRowWithError() throws IOException {
        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{null, "WHITE", "CLEANSING", null},
                new String[]{"Insomnia Relief", null, "CLEANSING", null},
                new String[]{"Insomnia Relief", "WHITE", null, null}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(3, result.skipped());
        assertEquals(0, result.created());
        assertTrue(result.errors().get(0).contains("Protocol Name"));
        assertTrue(result.errors().get(1).contains("Color Code"));
        assertTrue(result.errors().get(2).contains("Usage Type"));
        verifyNoInteractions(mappingRepo);
    }

    @Test
    void importFile_optionalChakraNameAbsent_savedWithNullChakraNameNoError() throws IOException {
        when(pranaColorRepo.findByCode("WHITE")).thenReturn(Optional.of(color(UUID.randomUUID(), "WHITE")));
        when(mappingRepo.findByProtocolNameAndHealerIdIsNull(anyString())).thenReturn(List.of());

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", null}
        ));

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(mappingRepo).save(argThat(m -> m.getChakraName() == null));
    }

    @Test
    void importFile_missingRequiredColumnConfig_throwsIllegalStateException_zeroRepoInteractions() throws IOException {
        // "Color Code" config row deleted entirely (WR-02) — colorIdConfig would be
        // null, silently disabling its required-ness check instead of failing fast.
        when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL_COLOR_MAPPING))
                .thenReturn(List.of(
                        columnConfig("Protocol Name", "protocolName", true),
                        columnConfig("Usage Type", "usageType", true),
                        columnConfig("Chakra Name", "chakraName", false)
                ));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", null}
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importFile(file, true, null));
        assertTrue(ex.getMessage().contains("colorId"));
        assertTrue(ex.getMessage().contains("PROTOCOL_COLOR_MAPPING"));
        verifyNoInteractions(mappingRepo);
    }

    @Test
    void importFile_unrecognizedTargetField_throwsIllegalStateException_zeroRepoInteractions() throws IOException {
        when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL_COLOR_MAPPING))
                .thenReturn(List.of(columnConfig("Bogus Column", "bogusField", true)));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"Insomnia Relief", "WHITE", "CLEANSING", null}
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importFile(file, true, null));
        assertTrue(ex.getMessage().contains("bogusField"));
        assertTrue(ex.getMessage().contains("PROTOCOL_COLOR_MAPPING"));
        verifyNoInteractions(mappingRepo);
    }
}
