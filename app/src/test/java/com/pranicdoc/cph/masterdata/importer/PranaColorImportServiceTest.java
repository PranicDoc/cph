package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PranaColorImportServiceTest {

    @Mock private PranaColorCatalogRepository pranaColorRepo;
    @Mock private MasterdataColumnConfigRepository columnConfigRepo;
    @Spy private SpreadsheetReader spreadsheetReader = new SpreadsheetReader();

    @InjectMocks private PranaColorImportService importService;

    private static final String[] HEADERS = {"Code", "Name", "Type", "Value", "Color Hex", "Description"};

    @BeforeEach
    void setUp() {
        lenient().when(pranaColorRepo.save(any(PranaColorCatalog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pranaColorRepo.findByCode(anyString())).thenReturn(Optional.empty());
        lenient().when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PRANA_COLOR))
                .thenReturn(pranaColorColumnConfigs());
    }

    private List<MasterdataColumnConfig> pranaColorColumnConfigs() {
        return List.of(
                columnConfig("Code", "code", true),
                columnConfig("Name", "name", true),
                columnConfig("Color Hex", "colorHex", false),
                columnConfig("Value", "properties", false),
                columnConfig("Description", "description", false)
        );
    }

    private MasterdataColumnConfig columnConfig(String columnName, String targetField, boolean required) {
        return MasterdataColumnConfig.builder()
                .masterdataType(MasterdataType.PRANA_COLOR)
                .columnName(columnName)
                .targetField(targetField)
                .required(required)
                .dataType(ColumnDataType.STRING)
                .systemDefined(true)
                .build();
    }

    // ---- fixture builders -------------------------------------------------

    /**
     * The design doc's Appendix (lines 284-337), verbatim: RED, ORANGE, YELLOW,
     * GREEN, BLUE fully enumerated (73 item rows); VIOLET, ELECTRIC_VIOLET, GOLD
     * as header-only rows (3 rows). 76 total data rows across 8 colours.
     */
    private List<String[]> buildFullDatasetRows() {
        List<String[]> rows = new ArrayList<>();

        addColorRows(rows, "RED", "Red Prana", "#FF0000",
                List.of("Strengthening", "Warm", "Expansive", "Dilating",
                        "Distributive (improves circulation)",
                        "Constructive — rapid tissue or cellular repair",
                        "Sustains the visible physical body",
                        "Vitalizes the blood, tissues, and the skeletal system",
                        "Stimulating and activating"),
                List.of("Strengthening sluggish and weakened organs or parts",
                        "Dilating blood vessels and air tubes",
                        "Improving circulation",
                        "Allergy",
                        "Internal and external wounds",
                        "General tiredness or weakening",
                        "Paralysis",
                        "Reviving unconscious patients",
                        "Reviving or prolonging the life of dying patients"));

        addColorRows(rows, "ORANGE", "Orange Prana", "#FFA500",
                List.of("Expelling", "Eliminative", "Decongesting", "Cleansing",
                        "Loosening — loosens diseased energy", "Melting",
                        "Extracting or abstracting",
                        "Splitting, exploding, and destructive"),
                List.of("Expelling or elimination of waste, toxins, germs, and diseased energy",
                        "Allergy", "Kidney and bladder ailments", "Constipation",
                        "Menstrual problems", "Removing blood clot", "Arthritis", "Cyst",
                        "Cold, cough, and lung problems"));

        addColorRows(rows, "YELLOW", "Yellow Prana", "#FFFF00",
                List.of("Cohesion or cementing",
                        "Assimilating, multiplying, and growing",
                        "Stimulating effect on the nerve",
                        "Initiating or starting",
                        "Necessary for strong and healthy tissue, organs, and bones"),
                List.of("Broken bones", "Skin problems", "Cellular repair",
                        "Developing strong healthy tissues, organs, and bones",
                        "Improving assimilation"));

        addColorRows(rows, "GREEN", "Green Prana", "#00FF00",
                List.of("Breaking down", "Digestive", "Decongesting", "Cleansing",
                        "Detoxifying", "Disinfecting", "Dissolving",
                        "Loosening of diseased bioplasmic matter",
                        "Destruction or breaking down of dead and diseased cells"),
                List.of("Breaking down of blood clot", "Disinfecting", "Cold", "Fever",
                        "Used in localized sweeping for decongesting and loosening stubborn diseased energy"));

        addColorRows(rows, "BLUE", "Blue Prana", "#0000FF",
                List.of("Disinfecting and disinflaming", "Inhibiting",
                        "Localizing and contracting",
                        "Soothing and mild anesthetic", "Cooling",
                        "Pliability or flexibility", "Blood clotting"),
                List.of("Ailments due to infection", "Removing pain",
                        "Reducing inflammation",
                        "Inhibiting chakras, organs, and motor action",
                        "Inducing rest and sleep", "Stopping bleeding",
                        "Bringing down fever"));

        addHeaderOnlyRow(rows, "VIOLET", "Violet Prana", "#EE82EE");
        addHeaderOnlyRow(rows, "ELECTRIC_VIOLET", "Electric Violet", "#7F00FF");
        addHeaderOnlyRow(rows, "GOLD", "Golden Prana", "#FFD700");

        return rows;
    }

    private void addColorRows(List<String[]> rows, String code, String name, String hex,
                               List<String> properties, List<String> applications) {
        for (String property : properties) {
            rows.add(new String[]{code, name, "PROPERTY", property, hex, ""});
        }
        for (String application : applications) {
            rows.add(new String[]{code, name, "APPLICATION", application, hex, ""});
        }
    }

    private void addHeaderOnlyRow(List<String[]> rows, String code, String name, String hex) {
        rows.add(new String[]{code, name, "", "", hex, ""});
    }

    private MockMultipartFile buildXlsx(List<String[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PranaColors");
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
            return new MockMultipartFile("file", "prana-colors.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private MockMultipartFile buildCsv(List<String[]> rows) {
        StringBuilder sb = new StringBuilder(String.join(",", HEADERS)).append("\n");
        for (String[] values : rows) {
            String[] escaped = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escaped[i] = csvEscape(values[i]);
            }
            sb.append(String.join(",", escaped)).append("\n");
        }
        return new MockMultipartFile("file", "prana-colors.csv", "text/csv",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ---- scenario (1): xlsx happy path, FULL 76-row/8-colour dataset ------

    @Test
    void importFile_xlsxHappyPath_fullDataset_createsAllEightColours() throws IOException {
        MockMultipartFile file = buildXlsx(buildFullDatasetRows());

        ImportResult result = importService.importFile(file, true, null);

        assertEquals(76, result.totalRows());
        assertEquals(8, result.created());
        assertEquals(0, result.updated());
        assertTrue(result.errors().isEmpty());

        ArgumentCaptor<PranaColorCatalog> captor = ArgumentCaptor.forClass(PranaColorCatalog.class);
        verify(pranaColorRepo, times(8)).save(captor.capture());
        Map<String, PranaColorCatalog> savedByCode = new HashMap<>();
        for (PranaColorCatalog saved : captor.getAllValues()) {
            savedByCode.put(saved.getCode(), saved);
        }

        assertColourCounts(savedByCode, "RED", 9, 9);
        assertColourCounts(savedByCode, "ORANGE", 8, 9);
        assertColourCounts(savedByCode, "YELLOW", 5, 5);
        assertColourCounts(savedByCode, "GREEN", 9, 5);
        assertColourCounts(savedByCode, "BLUE", 7, 7);
        assertColourCounts(savedByCode, "VIOLET", 0, 0);
        assertColourCounts(savedByCode, "ELECTRIC_VIOLET", 0, 0);
        assertColourCounts(savedByCode, "GOLD", 0, 0);

        assertFirstLast(savedByCode, "RED",
                "Strengthening", "Stimulating and activating",
                "Strengthening sluggish and weakened organs or parts",
                "Reviving or prolonging the life of dying patients");
        assertFirstLast(savedByCode, "ORANGE",
                "Expelling", "Splitting, exploding, and destructive",
                "Expelling or elimination of waste, toxins, germs, and diseased energy",
                "Cold, cough, and lung problems");
        assertFirstLast(savedByCode, "YELLOW",
                "Cohesion or cementing", "Necessary for strong and healthy tissue, organs, and bones",
                "Broken bones", "Improving assimilation");
        assertFirstLast(savedByCode, "GREEN",
                "Breaking down", "Destruction or breaking down of dead and diseased cells",
                "Breaking down of blood clot",
                "Used in localized sweeping for decongesting and loosening stubborn diseased energy");
        assertFirstLast(savedByCode, "BLUE",
                "Disinfecting and disinflaming", "Blood clotting",
                "Ailments due to infection", "Bringing down fever");
    }

    private void assertColourCounts(Map<String, PranaColorCatalog> savedByCode, String code,
                                     int expectedProperties, int expectedApplications) {
        PranaColorCatalog entity = savedByCode.get(code);
        assertNotNull(entity, "Expected saved colour for code " + code);
        assertEquals(expectedProperties, entity.getProperties().size(), code + " properties count");
        assertEquals(expectedApplications, entity.getApplications().size(), code + " applications count");
    }

    private void assertFirstLast(Map<String, PranaColorCatalog> savedByCode, String code,
                                  String firstProperty, String lastProperty,
                                  String firstApplication, String lastApplication) {
        PranaColorCatalog entity = savedByCode.get(code);
        List<String> properties = entity.getProperties();
        List<String> applications = entity.getApplications();
        assertEquals(firstProperty, properties.get(0), code + " first property");
        assertEquals(lastProperty, properties.get(properties.size() - 1), code + " last property");
        assertEquals(firstApplication, applications.get(0), code + " first application");
        assertEquals(lastApplication, applications.get(applications.size() - 1), code + " last application");
    }

    // ---- scenario (2): re-import replaces, does not double ----------------

    @Test
    void importFile_reimport_replacesNotDoublesLists() throws IOException {
        MockMultipartFile firstFile = buildXlsx(buildFullDatasetRows());
        ImportResult firstResult = importService.importFile(firstFile, true, null);
        assertEquals(8, firstResult.created());

        ArgumentCaptor<PranaColorCatalog> captor = ArgumentCaptor.forClass(PranaColorCatalog.class);
        verify(pranaColorRepo, times(8)).save(captor.capture());
        Map<String, PranaColorCatalog> savedByCode = new HashMap<>();
        for (PranaColorCatalog saved : captor.getAllValues()) {
            savedByCode.put(saved.getCode(), saved);
        }
        for (Map.Entry<String, PranaColorCatalog> entry : savedByCode.entrySet()) {
            when(pranaColorRepo.findByCode(entry.getKey())).thenReturn(Optional.of(entry.getValue()));
        }

        MockMultipartFile secondFile = buildXlsx(buildFullDatasetRows());
        ImportResult secondResult = importService.importFile(secondFile, true, null);

        assertEquals(0, secondResult.created());
        assertEquals(8, secondResult.updated());

        assertColourCounts(savedByCode, "RED", 9, 9);
        assertColourCounts(savedByCode, "ORANGE", 8, 9);
        assertColourCounts(savedByCode, "YELLOW", 5, 5);
        assertColourCounts(savedByCode, "GREEN", 9, 5);
        assertColourCounts(savedByCode, "BLUE", 7, 7);
    }

    // ---- scenario (3): csv happy path --------------------------------------

    @Test
    void importFile_csvHappyPath_createsColour() {
        List<String[]> rows = new ArrayList<>();
        addColorRows(rows, "YELLOW", "Yellow Prana", "#FFFF00",
                List.of("Cohesion or cementing",
                        "Assimilating, multiplying, and growing",
                        "Stimulating effect on the nerve",
                        "Initiating or starting",
                        "Necessary for strong and healthy tissue, organs, and bones"),
                List.of("Broken bones", "Skin problems", "Cellular repair",
                        "Developing strong healthy tissues, organs, and bones",
                        "Improving assimilation"));

        MockMultipartFile file = buildCsv(rows);
        ImportResult result = importService.importFile(file, true, null);

        assertEquals(10, result.totalRows());
        assertEquals(1, result.created());
        assertTrue(result.errors().isEmpty());
        verify(pranaColorRepo).save(argThat(c ->
                c.getCode().equals("YELLOW")
                        && c.getProperties().size() == 5
                        && c.getApplications().size() == 5
                        && c.getColorHex().equals("#FFFF00")
        ));
    }

    // ---- scenario (4): header-only row -------------------------------------

    @Test
    void importFile_headerOnlyRow_createsColourWithEmptyLists() throws IOException {
        List<String[]> rows = new ArrayList<>();
        addHeaderOnlyRow(rows, "VIOLET", "Violet Prana", "#EE82EE");

        MockMultipartFile file = buildXlsx(rows);
        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(pranaColorRepo).save(argThat(c ->
                c.getCode().equals("VIOLET")
                        && c.getProperties().isEmpty()
                        && c.getApplications().isEmpty()
        ));
    }

    // ---- scenario (5): blank Type + blank Value mid-group ignored ---------

    @Test
    void importFile_blankTypeAndValueMidGroup_ignoredNotSkipped() throws IOException {
        List<String[]> rows = List.of(
                new String[]{"RED", "Red Prana", "PROPERTY", "Strengthening", "#FF0000", ""},
                new String[]{"RED", "Red Prana", "", "", "#FF0000", ""},
                new String[]{"RED", "Red Prana", "PROPERTY", "Warm", "#FF0000", ""}
        );

        MockMultipartFile file = buildXlsx(rows);
        ImportResult result = importService.importFile(file, true, null);

        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        verify(pranaColorRepo).save(argThat(c ->
                c.getProperties().equals(List.of("Strengthening", "Warm"))
        ));
    }

    // ---- scenario (6): invalid Type -----------------------------------------

    @Test
    void importFile_invalidType_skipsRowButOtherColoursStillImport() throws IOException {
        List<String[]> rows = List.of(
                new String[]{"RED", "Red Prana", "FOO", "Strengthening", "#FF0000", ""},
                new String[]{"YELLOW", "Yellow Prana", "PROPERTY", "Cohesion or cementing", "#FFFF00", ""}
        );

        MockMultipartFile file = buildXlsx(rows);
        ImportResult result = importService.importFile(file, true, null);

        assertEquals(2, result.created());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("invalid Type")));
        verify(pranaColorRepo).save(argThat(c ->
                c.getCode().equals("RED") && c.getProperties().isEmpty()
        ));
        verify(pranaColorRepo).save(argThat(c ->
                c.getCode().equals("YELLOW") && c.getProperties().equals(List.of("Cohesion or cementing"))
        ));
    }

    // ---- scenario (7): missing Code -----------------------------------------

    @Test
    void importFile_missingCode_skipsRowWithError() throws IOException {
        List<String[]> rows = List.of(
                new String[]{"", "No Code Prana", "PROPERTY", "Something", "#000000", ""},
                new String[]{"RED", "Red Prana", "PROPERTY", "Strengthening", "#FF0000", ""}
        );

        MockMultipartFile file = buildXlsx(rows);
        ImportResult result = importService.importFile(file, true, null);

        assertEquals(1, result.created());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("missing Code")));
    }

    // ---- scenario (8): duplicate property value de-duplicated -------------

    @Test
    void importFile_duplicatePropertyValue_deduplicated() throws IOException {
        List<String[]> rows = List.of(
                new String[]{"RED", "Red Prana", "PROPERTY", "Strengthening", "#FF0000", ""},
                new String[]{"RED", "Red Prana", "PROPERTY", "Strengthening", "#FF0000", ""},
                new String[]{"RED", "Red Prana", "PROPERTY", "Warm", "#FF0000", ""}
        );

        MockMultipartFile file = buildXlsx(rows);
        ImportResult result = importService.importFile(file, true, null);

        assertEquals(3, result.totalRows());
        verify(pranaColorRepo).save(argThat(c ->
                c.getProperties().size() == 2
                        && c.getProperties().equals(List.of("Strengthening", "Warm"))
        ));
    }

    // ---- CR-01: row-ownership guard on the natural-key (Code) upsert -------

    @Test
    void importFile_nonSuperAdmin_cannotOverwriteSystemRow() throws IOException {
        PranaColorCatalog existing = new PranaColorCatalog();
        existing.setCode("RED");
        existing.setName("Red Prana");
        existing.setHealerId(null); // SYSTEM row
        when(pranaColorRepo.findByCode("RED")).thenReturn(Optional.of(existing));

        List<String[]> rows = List.<String[]>of(
                new String[]{"RED", "Red Prana", "PROPERTY", "Hostile overwrite", "#FF0000", ""}
        );
        MockMultipartFile file = buildXlsx(rows);

        ImportResult result = importService.importFile(file, false, UUID.randomUUID());

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("owned by another party")));
        assertTrue(existing.getProperties().isEmpty());
        verify(pranaColorRepo, never()).save(any());
    }

    @Test
    void importFile_nonSuperAdmin_cannotOverwriteAnotherHealersCustomRow() throws IOException {
        PranaColorCatalog existing = new PranaColorCatalog();
        existing.setCode("RED");
        existing.setName("Red Prana");
        existing.setHealerId(UUID.randomUUID()); // owned by a DIFFERENT healer
        when(pranaColorRepo.findByCode("RED")).thenReturn(Optional.of(existing));

        List<String[]> rows = List.<String[]>of(
                new String[]{"RED", "Red Prana", "PROPERTY", "Hostile overwrite", "#FF0000", ""}
        );
        MockMultipartFile file = buildXlsx(rows);

        ImportResult result = importService.importFile(file, false, UUID.randomUUID());

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("owned by another party")));
        assertTrue(existing.getProperties().isEmpty());
        verify(pranaColorRepo, never()).save(any());
    }

    @Test
    void importFile_nonSuperAdmin_newRow_stampsCallerHealerId() throws IOException {
        UUID callerHealerId = UUID.randomUUID();
        List<String[]> rows = List.<String[]>of(
                new String[]{"YELLOW", "Yellow Prana", "PROPERTY", "Cohesion or cementing", "#FFFF00", ""}
        );
        MockMultipartFile file = buildXlsx(rows);

        ImportResult result = importService.importFile(file, false, callerHealerId);

        assertEquals(1, result.created());
        verify(pranaColorRepo).save(argThat(c -> callerHealerId.equals(c.getHealerId())));
    }

    // ---- WR-02: D-17 fail-fast on a missing required-column-config row ----

    @Test
    void importFile_missingRequiredColumnConfig_throwsIllegalStateException() throws IOException {
        // "Code" config row deleted entirely — should fail fast up front rather than
        // silently skipping the required-ness check for that field.
        when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PRANA_COLOR))
                .thenReturn(List.of(columnConfig("Name", "name", true)));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"RED", "Red Prana", "PROPERTY", "Strengthening", "#FF0000", ""}
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importFile(file, true, null));
        assertTrue(ex.getMessage().contains("code"));
        assertTrue(ex.getMessage().contains("PRANA_COLOR"));
        verifyNoInteractions(pranaColorRepo);
    }

    // ---- scenario (9): D-17 fail-fast on unrecognized targetField ---------

    @Test
    void importFile_unrecognizedTargetField_throwsIllegalStateException() throws IOException {
        when(columnConfigRepo.findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PRANA_COLOR))
                .thenReturn(List.of(columnConfig("Bogus Column", "bogusField", true)));

        MockMultipartFile file = buildXlsx(List.<String[]>of(
                new String[]{"RED", "Red Prana", "PROPERTY", "Strengthening", "#FF0000", ""}
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importService.importFile(file, true, null));
        assertTrue(ex.getMessage().contains("bogusField"));
        assertTrue(ex.getMessage().contains("PRANA_COLOR"));
        verifyNoInteractions(pranaColorRepo);
    }
}
