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
import java.util.UUID;

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
    public ImportResult importFile(MultipartFile file) {
        // D-17 config-integrity fail-fast: reject the whole import up front if any
        // PROTOCOL_COLOR_MAPPING column-config row points at a targetField this
        // importer doesn't know how to write — before the spreadsheet is even parsed.
        List<MasterdataColumnConfig> columns = columnConfigRepo
                .findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL_COLOR_MAPPING);
        for (MasterdataColumnConfig config : columns) {
            validateTargetField(config.getTargetField());
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

            ProtocolColorMapping mapping = findExisting(protocolName, colorId, usageType);
            if (mapping == null) {
                mapping = new ProtocolColorMapping();
                mapping.setHealerId(null); // system row
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

    private ProtocolColorMapping findExisting(String protocolName, UUID colorId, ColorUsageType usageType) {
        return mappingRepo.findByProtocolNameAndHealerIdIsNull(protocolName).stream()
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
