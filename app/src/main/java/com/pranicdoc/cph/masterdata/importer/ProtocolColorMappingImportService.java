package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.PranaColorCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.ProtocolColorMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * MasterDataImporter for PROTOCOL_COLOR_MAPPING (MDIMPORT-12) — no prior
 * bulk-import path existed for this type (CRUD-only before this plan).
 *
 * <p>Columns are a fixed Java template, not a DB-configurable registry — the
 * masterdata_column_config table/API/registry was removed. Adding or
 * renaming a column is a code change to this class (plus the spreadsheet
 * template), not an admin action.
 *
 * <p>{@code colorId} is never written as a raw spreadsheet string — it is
 * always resolved through {@link PranaColorCatalogRepository#findByCode}.
 * Two independent row-level validations apply row-skip-with-collected-error
 * treatment, neither aborting the whole file: D-15 (unresolvable Color Code)
 * and D-16 (unresolvable Protocol Name, case-insensitive lookup against
 * {@code ProtocolCatalog} despite the denormalized field having no FK
 * constraint).
 */
@Service
@RequiredArgsConstructor
public class ProtocolColorMappingImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "ProtocolColorMappings";
    private static final String COL_PROTOCOL_NAME = "Protocol Name";
    private static final String COL_COLOR_CODE = "Color Code";
    private static final String COL_USAGE_TYPE = "Usage Type";
    private static final String COL_CHAKRA_NAME = "Chakra Name";

    private final ProtocolColorMappingRepository mappingRepo;
    private final PranaColorCatalogRepository pranaColorRepo;
    private final ProtocolCatalogRepository protocolRepo;
    private final SpreadsheetReader spreadsheetReader;

    @Override
    public MasterdataType kind() {
        return MasterdataType.PROTOCOL_COLOR_MAPPING;
    }

    @Override
    @Transactional
    public ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId) {
        List<Map<String, String>> rows = spreadsheetReader.read(file, SHEET_NAME);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;

            String protocolName = trimToNull(row.get(COL_PROTOCOL_NAME));
            String colorCode = trimToNull(row.get(COL_COLOR_CODE));
            String usageTypeRaw = trimToNull(row.get(COL_USAGE_TYPE));
            String chakraName = trimToNull(row.get(COL_CHAKRA_NAME));

            String skipError = null;

            if (protocolName == null) {
                skipError = "Row " + rowNumber + ": missing " + COL_PROTOCOL_NAME;
            } else if (colorCode == null) {
                skipError = "Row " + rowNumber + ": missing " + COL_COLOR_CODE;
            } else if (usageTypeRaw == null) {
                skipError = "Row " + rowNumber + ": missing " + COL_USAGE_TYPE;
            }

            // D-16: Protocol Name validated against ProtocolCatalog, case-insensitive,
            // despite no FK constraint on this denormalized field.
            if (skipError == null && !protocolRepo.existsByNameIgnoreCase(protocolName)) {
                skipError = "Row " + rowNumber + ": Protocol Name '" + protocolName + "' not found";
            }

            // D-15: Color Code resolved through PranaColorCatalog.findByCode — colorId is
            // never a raw string write.
            UUID colorId = null;
            if (skipError == null) {
                Optional<PranaColorCatalog> color = pranaColorRepo.findByCode(colorCode);
                if (color.isEmpty()) {
                    skipError = "Row " + rowNumber + ": Color Code '" + colorCode + "' not found";
                } else {
                    colorId = color.get().getId();
                }
            }

            ColorUsageType usageType = null;
            if (skipError == null) {
                usageType = parseUsageType(usageTypeRaw);
                if (usageType == null) {
                    skipError = "Row " + rowNumber + ": invalid " + COL_USAGE_TYPE
                            + " '" + usageTypeRaw + "' — must be CLEANSING or ENERGISING";
                }
            }

            if (skipError != null) {
                errors.add(skipError);
                skipped++;
                continue;
            }

            ProtocolColorMapping mapping = findExisting(protocolName, colorId, usageType, healerId);
            // PD-40-04-style guard: a SYSTEM row (healerId == null) can only be
            // overwritten by SUPADM; another healer's custom row can only be
            // overwritten by that healer or SUPADM. (Note: MasterDataService's
            // single-row CRUD path currently only enforces the SYSTEM-row half of
            // this — see MASTERDATA-XXX if tracking that gap.)
            // (WR-01: findExisting also searches the caller's own healerId-scoped rows —
            // see findByProtocolNameAndHealerId — so a healer's own custom mapping is found
            // and updated in place on re-import instead of being duplicated. The "other
            // healer's row" branch below is reachable when a different, non-caller healerId
            // is passed to isSuperAdmin-driven bulk imports done on another healer's behalf.)
            if (mapping != null && (mapping.getHealerId() == null
                    ? !isSuperAdmin
                    : !Objects.equals(mapping.getHealerId(), healerId) && !isSuperAdmin)) {
                errors.add("Row " + rowNumber + ": mapping for protocol '" + protocolName
                        + "' is owned by another party — not permitted to overwrite");
                skipped++;
                continue;
            }
            if (mapping == null) {
                mapping = new ProtocolColorMapping();
                // Non-SUPADM callers can never create a SYSTEM (healerId == null) row via
                // import — mirrors MasterDataService's create-mapping path always stamping
                // the caller's own healerId on new rows via the CRUD path.
                mapping.setHealerId(isSuperAdmin ? null : healerId);
                mapping.setProtocolName(protocolName);
                mapping.setColorId(colorId);
                mapping.setUsageType(usageType);
                created++;
            } else {
                updated++;
            }
            mapping.setChakraName(chakraName);
            mappingRepo.save(mapping);
        }

        return new ImportResult(rows.size(), created, updated, skipped, errors);
    }

    private ProtocolColorMapping findExisting(String protocolName, UUID colorId, ColorUsageType usageType, UUID healerId) {
        // WR-01: search both the SYSTEM-row pool (healerId == null) and, when the caller
        // is a non-SUPADM healer, that healer's own previously-imported custom rows —
        // otherwise re-importing the same file as that healer can never find its own
        // existing mapping and always inserts a duplicate instead of updating in place.
        List<ProtocolColorMapping> candidates = new ArrayList<>(
                mappingRepo.findByProtocolNameAndHealerIdIsNull(protocolName));
        if (healerId != null) {
            candidates.addAll(mappingRepo.findByProtocolNameAndHealerId(protocolName, healerId));
        }
        return candidates.stream()
                .filter(m -> Objects.equals(m.getColorId(), colorId) && m.getUsageType() == usageType)
                .findFirst()
                .orElse(null);
    }

    private ColorUsageType parseUsageType(String raw) {
        if (raw == null) return null;
        try {
            return ColorUsageType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
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
