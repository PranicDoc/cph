package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.MasterDataImporter;
import com.pranicdoc.cph.masterdata.importer.ProtocolChakraColorEnricher;
import com.pranicdoc.cph.masterdata.importer.SpreadsheetReader;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Columns are a fixed Java template, not a DB-configurable registry — the
 * masterdata_column_config table/API/registry was removed. Adding or
 * renaming a column is a code change to this class (plus the spreadsheet
 * template), not an admin action.
 *
 * <p><b>MDIMPORT-22 — optional chakra/color enrichment:</b> this importer
 * also recognizes an entirely separate, optional set of columns
 * ("Chakras used"/"Cleansing colour"/"Energizing colour") that enrich a
 * protocol with {@link com.pranicdoc.cph.masterdata.model.ProtocolChakraColorUsage}
 * rows via {@link ProtocolChakraColorEnricher}. Detection is column-presence
 * based, not a separate {@code type}: a plain protocol file (today's shape)
 * takes the original, byte-for-byte-unchanged per-row path below; a file
 * that also carries the chakra/color columns takes a grouped path that can
 * either enrich an already-existing protocol (decoupled — the protocol
 * columns are simply absent) or do both in one pass (a future combined
 * file). See {@code ProtocolImportServiceTest} for all three shapes.
 */
@Service
@RequiredArgsConstructor
public class ProtocolImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "Protocols";
    private static final String CHAKRA_COLOR_SHEET_NAME = "Sheet2";
    private static final String COL_NAME = "Protocol/Technique Name";
    private static final String COL_NAME_ALT = "Protocol name";
    private static final String COL_BOOK = "Book";
    private static final String COL_PAGE = "Page";
    private static final String COL_CATEGORY = "Category";
    private static final String COL_DESCRIPTION = "Description";
    private static final String COL_CHAPTER = "Chapter";
    private static final String COL_TYPE = "Type";
    private static final String COL_CHAKRAS_USED = "Chakras used";
    private static final String COL_CLEANSING = "Cleansing colour";
    private static final String COL_ENERGIZING = "Energizing colour";

    private final ProtocolCatalogRepository protocolRepo;
    private final SpreadsheetReader spreadsheetReader;
    private final ProtocolChakraColorEnricher chakraColorEnricher;

    @Override
    public MasterdataType kind() {
        return MasterdataType.PROTOCOL;
    }

    @Override
    @Transactional
    public ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId) {
        List<Map<String, String>> rows = spreadsheetReader.read(file, List.of(SHEET_NAME, CHAKRA_COLOR_SHEET_NAME));

        boolean hasChakraColorColumns = !rows.isEmpty() && (rows.get(0).containsKey(COL_CHAKRAS_USED)
                || rows.get(0).containsKey(COL_CLEANSING) || rows.get(0).containsKey(COL_ENERGIZING));

        if (hasChakraColorColumns) {
            return importWithChakraColorEnrichment(rows, isSuperAdmin, healerId);
        }

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
            String category = trimToNull(row.get(COL_CATEGORY));
            String description = trimToNull(row.get(COL_DESCRIPTION));
            String chapter = trimToNull(row.get(COL_CHAPTER));
            String typeRaw = trimToNull(row.get(COL_TYPE));

            String skipError = null;
            if (name == null) {
                skipError = "Row " + rowNumber + ": missing " + COL_NAME;
            } else if (sourceBook == null) {
                skipError = "Row " + rowNumber + ": missing " + COL_BOOK;
            } else if (pageRaw == null) {
                skipError = "Row " + rowNumber + ": missing " + COL_PAGE;
            } else if (typeRaw == null) {
                skipError = "Row " + rowNumber + ": missing " + COL_TYPE;
            }

            Integer pageNumber = null;
            if (skipError == null) {
                pageNumber = parsePageNumber(pageRaw);
                if (pageNumber == null) {
                    skipError = "Row " + rowNumber + ": " + COL_PAGE + " '" + pageRaw + "' is not numeric";
                }
            }

            ProtocolType protocolType = null;
            if (skipError == null) {
                protocolType = parseProtocolType(typeRaw);
                if (protocolType == null) {
                    skipError = "Row " + rowNumber + ": invalid " + COL_TYPE
                            + " '" + typeRaw + "' — must be PROTOCOL or SUPPLEMENTARY";
                }
            }

            if (skipError != null) {
                errors.add(skipError);
                skipped++;
                continue;
            }

            ProtocolCatalog protocol = protocolRepo
                    .findByNameAndSourceBookAndPageNumber(name, sourceBook, pageNumber)
                    .orElse(null);
            // PD-40-04-style guard: a SYSTEM row (healerId == null) can only be
            // overwritten by SUPADM; another healer's custom row can only be
            // overwritten by that healer or SUPADM. (Note: MasterDataService's
            // single-row CRUD path currently only enforces the SYSTEM-row half of
            // this — see MASTERDATA-XXX if tracking that gap.)
            if (protocol != null && (protocol.getHealerId() == null
                    ? !isSuperAdmin
                    : !Objects.equals(protocol.getHealerId(), healerId) && !isSuperAdmin)) {
                errors.add("Row " + rowNumber + ": protocol '" + name
                        + "' is owned by another party — not permitted to overwrite");
                skipped++;
                continue;
            }
            if (protocol == null) {
                protocol = new ProtocolCatalog();
                protocol.setName(name);
                protocol.setSourceBook(sourceBook);
                protocol.setPageNumber(pageNumber);
                // Non-SUPADM callers can never create a SYSTEM (healerId == null) row via
                // import — mirrors MasterDataService.createProtocol always stamping the
                // caller's own healerId on new rows via the CRUD path.
                protocol.setHealerId(isSuperAdmin ? null : healerId);
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

        return new ImportResult(rows.size(), created, updated, skipped, errors);
    }

    /**
     * MDIMPORT-22: the grouped chakra/color-enrichment path. Advanced-pH-shaped
     * rows repeat the protocol name only on the first row of a group, adding
     * one more chakra/color row per group member until the next non-blank
     * name starts a new group (mirrors {@code PranaColorImportService}'s
     * own group-by-leading-key idiom, adapted for blank-continuation rows
     * instead of a repeated key).
     */
    private ImportResult importWithChakraColorEnrichment(List<Map<String, String>> rows, boolean isSuperAdmin, UUID healerId) {
        boolean hasProtocolColumns = !rows.isEmpty()
                && (rows.get(0).containsKey(COL_BOOK) || rows.get(0).containsKey(COL_TYPE));

        List<Group> groups = new ArrayList<>();
        Group current = null;
        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;
            String name = resolveName(row);
            if (name != null) {
                current = new Group(name, rowNumber);
                groups.add(current);
            }
            if (current == null) {
                continue; // fully-blank separator row before any group has started
            }
            current.rows.add(new RowEntry(rowNumber, row));
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Group group : groups) {
            Map<String, String> leadRow = group.rows.get(0).row();
            String protocolName = group.name;

            if (hasProtocolColumns) {
                ProtocolUpsertOutcome outcome = upsertProtocolFromRow(leadRow, isSuperAdmin, healerId, group.leadRowNumber);
                if (outcome.error() != null) {
                    errors.add(outcome.error());
                    skipped++;
                    continue; // whole group skipped — nothing to attach chakra/color data to
                }
                protocolName = outcome.protocol().getName();
                if (outcome.wasCreated()) {
                    created++;
                } else {
                    updated++;
                }
            } else if (!protocolRepo.existsByNameIgnoreCase(protocolName)) {
                errors.add("Row " + group.leadRowNumber + ": protocol '" + protocolName
                        + "' not found — import the protocol first");
                skipped++;
                continue;
            }

            for (RowEntry entry : group.rows) {
                String chakraName = trimToNull(entry.row().get(COL_CHAKRAS_USED));
                List<String> cleansing = splitColorPhrases(entry.row().get(COL_CLEANSING));
                List<String> energizing = splitColorPhrases(entry.row().get(COL_ENERGIZING));
                if (chakraName == null && cleansing.isEmpty() && energizing.isEmpty()) {
                    continue; // no chakra/color data on this particular row — nothing to enrich
                }

                ProtocolChakraColorEnricher.EnrichmentResult result = chakraColorEnricher.enrich(
                        protocolName, chakraName, cleansing, energizing, isSuperAdmin, healerId,
                        "Row " + entry.rowNumber());
                if (result.created()) {
                    created++;
                } else if (result.updated()) {
                    updated++;
                } else {
                    skipped++;
                    errors.add(result.error());
                }
            }
        }

        return new ImportResult(rows.size(), created, updated, skipped, errors);
    }

    private String resolveName(Map<String, String> row) {
        String name = trimToNull(row.get(COL_NAME));
        return name != null ? name : trimToNull(row.get(COL_NAME_ALT));
    }

    /** Splits on "+" (the source data's multi-color-per-cell delimiter), trims each piece, drops blanks. */
    private List<String> splitColorPhrases(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> phrases = new ArrayList<>();
        for (String piece : raw.split("\\+")) {
            String trimmed = piece.trim();
            if (!trimmed.isEmpty()) {
                phrases.add(trimmed);
            }
        }
        return phrases;
    }

    private record ProtocolUpsertOutcome(ProtocolCatalog protocol, boolean wasCreated, String error) {
    }

    /**
     * The same validate/parse/ownership-guard/upsert steps as the unchanged
     * per-row loop above, applied to one lead row of a chakra/color group
     * instead of every row in the file. Deliberately a separate method
     * rather than a shared extraction — keeps the original, already-shipped
     * loop untouched, at the cost of duplicating this logic once.
     */
    private ProtocolUpsertOutcome upsertProtocolFromRow(Map<String, String> row, boolean isSuperAdmin, UUID healerId, int rowNumber) {
        String name = resolveName(row);
        String sourceBook = trimToNull(row.get(COL_BOOK));
        String pageRaw = trimToNull(row.get(COL_PAGE));
        String category = trimToNull(row.get(COL_CATEGORY));
        String description = trimToNull(row.get(COL_DESCRIPTION));
        String chapter = trimToNull(row.get(COL_CHAPTER));
        String typeRaw = trimToNull(row.get(COL_TYPE));

        if (name == null) {
            return new ProtocolUpsertOutcome(null, false, "Row " + rowNumber + ": missing " + COL_NAME);
        }
        if (sourceBook == null) {
            return new ProtocolUpsertOutcome(null, false, "Row " + rowNumber + ": missing " + COL_BOOK);
        }
        if (pageRaw == null) {
            return new ProtocolUpsertOutcome(null, false, "Row " + rowNumber + ": missing " + COL_PAGE);
        }
        if (typeRaw == null) {
            return new ProtocolUpsertOutcome(null, false, "Row " + rowNumber + ": missing " + COL_TYPE);
        }

        Integer pageNumber = parsePageNumber(pageRaw);
        if (pageNumber == null) {
            return new ProtocolUpsertOutcome(null, false, "Row " + rowNumber + ": " + COL_PAGE + " '" + pageRaw + "' is not numeric");
        }
        ProtocolType protocolType = parseProtocolType(typeRaw);
        if (protocolType == null) {
            return new ProtocolUpsertOutcome(null, false, "Row " + rowNumber + ": invalid " + COL_TYPE
                    + " '" + typeRaw + "' — must be PROTOCOL or SUPPLEMENTARY");
        }

        ProtocolCatalog protocol = protocolRepo
                .findByNameAndSourceBookAndPageNumber(name, sourceBook, pageNumber)
                .orElse(null);
        if (protocol != null && (protocol.getHealerId() == null
                ? !isSuperAdmin
                : !Objects.equals(protocol.getHealerId(), healerId) && !isSuperAdmin)) {
            return new ProtocolUpsertOutcome(null, false, "Row " + rowNumber + ": protocol '" + name
                    + "' is owned by another party — not permitted to overwrite");
        }

        boolean wasCreated = protocol == null;
        if (wasCreated) {
            protocol = new ProtocolCatalog();
            protocol.setName(name);
            protocol.setSourceBook(sourceBook);
            protocol.setPageNumber(pageNumber);
            protocol.setHealerId(isSuperAdmin ? null : healerId);
        }
        protocol.setCategory(category);
        protocol.setDescription(description);
        protocol.setChapter(chapter);
        protocol.setProtocolType(protocolType);
        protocolRepo.save(protocol);

        return new ProtocolUpsertOutcome(protocol, wasCreated, null);
    }

    private static final class Group {
        final String name;
        final int leadRowNumber;
        final List<RowEntry> rows = new ArrayList<>();

        Group(String name, int leadRowNumber) {
            this.name = name;
            this.leadRowNumber = leadRowNumber;
        }
    }

    private record RowEntry(int rowNumber, Map<String, String> row) {
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
}
