package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MasterDataImporter for PROTOCOL_COLOR_MAPPING (MDIMPORT-12) — no prior
 * bulk-import path existed for this type (CRUD-only before this plan).
 *
 * <p>The one importer whose targetField mapping (D-11) includes a
 * lookup-backed field: {@code colorId} is never written as a raw spreadsheet
 * string, it is always resolved through {@link
 * PranaColorCatalogRepository#findByCode}. Two independent row-level
 * validations apply row-skip-with-collected-error treatment, neither aborting
 * the whole file: D-15 (unresolvable Color Code) and D-16 (unresolvable
 * Protocol Name, case-insensitive lookup against {@code ProtocolCatalog}
 * despite the denormalized field having no FK constraint).
 */
@Service
@RequiredArgsConstructor
public class ProtocolColorMappingImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "ProtocolColorMappings";

    private final ProtocolColorMappingRepository mappingRepo;
    private final PranaColorCatalogRepository pranaColorRepo;
    private final ProtocolCatalogRepository protocolRepo;
    private final SpreadsheetReader spreadsheetReader;
    private final MasterdataColumnConfigRepository columnConfigRepo;

    @Override
    public MasterdataType kind() {
        return MasterdataType.PROTOCOL_COLOR_MAPPING;
    }

    @Override
    @Transactional
    public ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId) {
        // D-17 config-integrity fail-fast: reject the whole import up front if any
        // PROTOCOL_COLOR_MAPPING column-config row points at a targetField this
        // importer doesn't know how to write — before the spreadsheet is even parsed.
        List<MasterdataColumnConfig> columns = columnConfigRepo
                .findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL_COLOR_MAPPING);
        for (MasterdataColumnConfig config : columns) {
            validateTargetField(config.getTargetField());
        }
        // D-17 fail-fast, part 2: also assert every targetField this importer needs is
        // actually present among the fetched columns — not just that every present row
        // is recognized. Otherwise a deleted required config row (e.g. "Color Code")
        // silently disables that field's required-ness check below, letting every row
        // write a null FK-backed column and abort the whole transactional import with an
        // unhandled DataIntegrityViolationException mid-loop instead of a clean fail-fast.
        Set<String> seenTargetFields = columns.stream()
                .map(MasterdataColumnConfig::getTargetField)
                .collect(Collectors.toSet());
        for (String required : Set.of("protocolName", "colorId", "usageType")) {
            if (!seenTargetFields.contains(required)) {
                throw new IllegalStateException("Missing required targetField '" + required
                        + "' in masterdata_column_config for PROTOCOL_COLOR_MAPPING — config/importer drift");
            }
        }

        List<Map<String, String>> rows = spreadsheetReader.read(file, SHEET_NAME);

        MasterdataColumnConfig protocolNameConfig = findConfig(columns, "protocolName");
        MasterdataColumnConfig colorIdConfig = findConfig(columns, "colorId");
        MasterdataColumnConfig usageTypeConfig = findConfig(columns, "usageType");
        MasterdataColumnConfig chakraNameConfig = findConfig(columns, "chakraName");

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;

            String protocolName = protocolNameConfig == null ? null : trimToNull(row.get(protocolNameConfig.getColumnName()));
            String colorCode = colorIdConfig == null ? null : trimToNull(row.get(colorIdConfig.getColumnName()));
            String usageTypeRaw = usageTypeConfig == null ? null : trimToNull(row.get(usageTypeConfig.getColumnName()));
            String chakraName = chakraNameConfig == null ? null : trimToNull(row.get(chakraNameConfig.getColumnName()));

            String skipError = null;

            if (protocolNameConfig != null && protocolNameConfig.isRequired() && protocolName == null) {
                skipError = "Row " + rowNumber + ": missing " + protocolNameConfig.getColumnName();
            } else if (colorIdConfig != null && colorIdConfig.isRequired() && colorCode == null) {
                skipError = "Row " + rowNumber + ": missing " + colorIdConfig.getColumnName();
            } else if (usageTypeConfig != null && usageTypeConfig.isRequired() && usageTypeRaw == null) {
                skipError = "Row " + rowNumber + ": missing " + usageTypeConfig.getColumnName();
            }

            // D-16: Protocol Name validated against ProtocolCatalog, case-insensitive,
            // despite no FK constraint on this denormalized field.
            if (skipError == null && protocolName != null && !protocolRepo.existsByNameIgnoreCase(protocolName)) {
                skipError = "Row " + rowNumber + ": Protocol Name '" + protocolName + "' not found";
            }

            // D-15: Color Code resolved through PranaColorCatalog.findByCode — colorId is
            // never a raw string write.
            UUID colorId = null;
            if (skipError == null && colorCode != null) {
                Optional<PranaColorCatalog> color = pranaColorRepo.findByCode(colorCode);
                if (color.isEmpty()) {
                    skipError = "Row " + rowNumber + ": Color Code '" + colorCode + "' not found";
                } else {
                    colorId = color.get().getId();
                }
            }

            ColorUsageType usageType = null;
            if (skipError == null && usageTypeRaw != null) {
                usageType = parseUsageType(usageTypeRaw);
                if (usageType == null) {
                    String columnLabel = usageTypeConfig != null ? usageTypeConfig.getColumnName() : "Usage Type";
                    skipError = "Row " + rowNumber + ": invalid " + columnLabel
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
            // (WR-01: findExisting now also searches the caller's own healerId-scoped rows —
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

    private MasterdataColumnConfig findConfig(List<MasterdataColumnConfig> columns, String targetField) {
        return columns.stream()
                .filter(c -> targetField.equals(c.getTargetField()))
                .findFirst()
                .orElse(null);
    }

    private void validateTargetField(String targetField) {
        switch (targetField) {
            case "protocolName", "colorId", "usageType", "chakraName" -> {
                // recognized — no-op
            }
            default -> throw new IllegalStateException("Unknown targetField '" + targetField
                    + "' in masterdata_column_config for PROTOCOL_COLOR_MAPPING — config/importer drift");
        }
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
