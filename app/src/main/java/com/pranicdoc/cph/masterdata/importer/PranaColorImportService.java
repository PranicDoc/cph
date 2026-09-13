package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@code MasterDataImporter} for {@link MasterdataType#PRANA_COLOR} (MDIMPORT-10).
 *
 * <p>Unlike the other importers in this phase, the "PranaColors" spreadsheet is
 * long-format — one row per PROPERTY/APPLICATION item, colour repeated — so rows
 * are grouped by {@code Code} (trimmed, uppercased) before being applied to a
 * single {@link PranaColorCatalog} per group.
 *
 * <p>Columns are a fixed Java template, not a DB-configurable registry — the
 * masterdata_column_config table/API/registry was removed. Adding or renaming
 * a column is a code change to this class (plus the spreadsheet template), not
 * an admin action.
 *
 * <p><b>{@link ImportResult} semantics differ from {@code ProtocolImportService}:</b>
 * {@code totalRows} is still the total parsed data-row count, but {@code created}
 * and {@code updated} count COLOURS (upserted {@code PranaColorCatalog} rows),
 * not spreadsheet rows, and {@code skipped} counts error ROWS (collected in
 * {@code errors}), not error colours.
 */
@Service
@RequiredArgsConstructor
public class PranaColorImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "PranaColors";
    private static final String COL_CODE = "Code";
    private static final String COL_NAME = "Name";
    private static final String COL_TYPE = "Type";
    private static final String COL_VALUE = "Value";
    private static final String COL_COLOR_HEX = "Color Hex";
    private static final String COL_DESCRIPTION = "Description";
    private static final String TYPE_PROPERTY = "PROPERTY";
    private static final String TYPE_APPLICATION = "APPLICATION";

    private final PranaColorCatalogRepository pranaColorRepo;
    private final SpreadsheetReader spreadsheetReader;

    @Override
    public MasterdataType kind() {
        return MasterdataType.PRANA_COLOR;
    }

    @Override
    @Transactional
    public ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId) {
        List<Map<String, String>> rows = spreadsheetReader.read(file, SHEET_NAME);

        List<String> errors = new ArrayList<>();

        // Group by trimmed+uppercased Code, preserving first-seen order. A row with a
        // missing/blank Code is a row error and is not added to any group.
        Map<String, List<RowEntry>> groups = new LinkedHashMap<>();
        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;

            String code = trimToNull(row.get(COL_CODE));
            if (code == null) {
                errors.add("Row " + rowNumber + ": missing Code");
                continue;
            }
            String normalizedCode = code.toUpperCase(Locale.ROOT);
            groups.computeIfAbsent(normalizedCode, k -> new ArrayList<>())
                    .add(new RowEntry(rowNumber, row));
        }

        int created = 0;
        int updated = 0;

        for (Map.Entry<String, List<RowEntry>> group : groups.entrySet()) {
            String code = group.getKey();
            List<RowEntry> groupRows = group.getValue();

            RowEntry firstEntry = groupRows.get(0);
            String name = trimToNull(firstEntry.row().get(COL_NAME));
            if (name == null) {
                errors.add("Row " + firstEntry.rowNumber() + ": missing Name");
                continue; // whole colour skipped — Name is required from the group's first row
            }
            String colorHex = trimToNull(firstEntry.row().get(COL_COLOR_HEX));
            String description = trimToNull(firstEntry.row().get(COL_DESCRIPTION));

            List<String> properties = new ArrayList<>();
            List<String> applications = new ArrayList<>();
            Set<String> propertiesSeen = new LinkedHashSet<>();
            Set<String> applicationsSeen = new LinkedHashSet<>();

            for (RowEntry entry : groupRows) {
                String type = trimToNull(entry.row().get(COL_TYPE));
                String value = trimToNull(entry.row().get(COL_VALUE));

                if (type == null && value == null) {
                    continue; // header-only row — no-op, not an error
                }
                if (type == null || value == null) {
                    errors.add("Row " + entry.rowNumber() + ": Type and Value must both be present or both blank");
                    continue;
                }

                String normalizedType = type.toUpperCase(Locale.ROOT);
                if (TYPE_PROPERTY.equals(normalizedType)) {
                    addDeduped(properties, propertiesSeen, value);
                } else if (TYPE_APPLICATION.equals(normalizedType)) {
                    addDeduped(applications, applicationsSeen, value);
                } else {
                    errors.add("Row " + entry.rowNumber() + ": invalid Type '" + type + "' — must be PROPERTY or APPLICATION");
                }
            }

            PranaColorCatalog entity = pranaColorRepo.findByCode(code).orElse(null);
            // PD-40-04-style guard: a SYSTEM row (healerId == null) can only be
            // overwritten by SUPADM; another healer's custom row can only be
            // overwritten by that healer or SUPADM. (Note: MasterDataService's
            // single-row CRUD path currently only enforces the SYSTEM-row half of
            // this — see MASTERDATA-XXX if tracking that gap.)
            if (entity != null && (entity.getHealerId() == null
                    ? !isSuperAdmin
                    : !Objects.equals(entity.getHealerId(), healerId) && !isSuperAdmin)) {
                errors.add("Row " + firstEntry.rowNumber() + ": code '" + code
                        + "' is owned by another party — not permitted to overwrite");
                continue;
            }
            if (entity == null) {
                entity = new PranaColorCatalog();
                // Non-SUPADM callers can never create a SYSTEM (healerId == null) row via
                // import — mirrors MasterDataService.createColor always stamping the
                // caller's own healerId on new rows via the CRUD path.
                entity.setHealerId(isSuperAdmin ? null : healerId);
                created++;
            } else {
                updated++;
            }

            entity.setCode(code);
            entity.setName(name);
            entity.setColorHex(colorHex);
            entity.setDescription(description);
            // Replace (not append) — re-import must replace each colour's lists.
            entity.setProperties(properties);
            entity.setApplications(applications);

            pranaColorRepo.save(entity);
        }

        return new ImportResult(rows.size(), created, updated, errors.size(), errors);
    }

    private void addDeduped(List<String> target, Set<String> seenLowerCase, String value) {
        if (seenLowerCase.add(value.toLowerCase(Locale.ROOT))) {
            target.add(value);
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RowEntry(int rowNumber, Map<String, String> row) {
    }
}
