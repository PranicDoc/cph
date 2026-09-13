package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.MasterDataImporter;
import com.pranicdoc.cph.masterdata.importer.SpreadsheetReader;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
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

@Service
@RequiredArgsConstructor
public class ProtocolImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "Protocols";

    private final ProtocolCatalogRepository protocolRepo;
    private final SpreadsheetReader spreadsheetReader;
    private final MasterdataColumnConfigRepository columnConfigRepo;

    @Override
    public MasterdataType kind() {
        return MasterdataType.PROTOCOL;
    }

    @Override
    @Transactional
    public ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId) {
        // D-17 config-integrity fail-fast: reject the whole import up front if any
        // PROTOCOL column-config row points at a targetField this importer doesn't
        // know how to write — before the spreadsheet is even parsed.
        List<MasterdataColumnConfig> columns = columnConfigRepo
                .findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.PROTOCOL);
        for (MasterdataColumnConfig config : columns) {
            validateTargetField(config.getTargetField());
        }

        List<Map<String, String>> rows = spreadsheetReader.read(file, SHEET_NAME);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;

            ProtocolCatalog values = new ProtocolCatalog();
            String skipError = null;

            for (MasterdataColumnConfig config : columns) {
                String targetField = config.getTargetField();
                String rawValue = trimToNull(row.get(config.getColumnName()));

                if (config.isRequired() && rawValue == null) {
                    skipError = "Row " + rowNumber + ": missing " + config.getColumnName();
                    break;
                }
                if (rawValue == null) {
                    continue;
                }

                if ("pageNumber".equals(targetField) && parsePageNumber(rawValue) == null) {
                    skipError = "Row " + rowNumber + ": " + config.getColumnName()
                            + " '" + rawValue + "' is not numeric";
                    break;
                }
                if ("protocolType".equals(targetField) && parseProtocolType(rawValue) == null) {
                    skipError = "Row " + rowNumber + ": invalid " + config.getColumnName()
                            + " '" + rawValue + "' — must be PROTOCOL or SUPPLEMENTARY";
                    break;
                }

                applyColumn(values, targetField, rawValue);
            }

            if (skipError != null) {
                errors.add(skipError);
                skipped++;
                continue;
            }

            ProtocolCatalog protocol = protocolRepo
                    .findByNameAndSourceBookAndPageNumber(values.getName(), values.getSourceBook(), values.getPageNumber())
                    .orElse(null);
            // PD-40-04: same row-ownership guard MasterDataService.updateProtocol enforces —
            // a SYSTEM row (healerId == null) can only be overwritten by SUPADM; another
            // healer's custom row can only be overwritten by that healer or SUPADM.
            if (protocol != null && (protocol.getHealerId() == null
                    ? !isSuperAdmin
                    : !Objects.equals(protocol.getHealerId(), healerId) && !isSuperAdmin)) {
                errors.add("Row " + rowNumber + ": protocol '" + values.getName()
                        + "' is owned by another party — not permitted to overwrite");
                skipped++;
                continue;
            }
            if (protocol == null) {
                protocol = values;
                // Non-SUPADM callers can never create a SYSTEM (healerId == null) row via
                // import — mirrors MasterDataService.createProtocol always stamping the
                // caller's own healerId on new rows via the CRUD path.
                protocol.setHealerId(isSuperAdmin ? null : healerId);
                created++;
            } else {
                protocol.setCategory(values.getCategory());
                protocol.setDescription(values.getDescription());
                protocol.setChapter(values.getChapter());
                protocol.setProtocolType(values.getProtocolType());
                updated++;
            }
            protocolRepo.save(protocol);
        }

        return new ImportResult(rows.size(), created, updated, skipped, errors);
    }

    private void applyColumn(ProtocolCatalog entity, String targetField, String rawValue) {
        switch (targetField) {
            case "name" -> entity.setName(rawValue);
            case "sourceBook" -> entity.setSourceBook(rawValue);
            case "pageNumber" -> entity.setPageNumber(parsePageNumber(rawValue));
            case "category" -> entity.setCategory(rawValue);
            case "description" -> entity.setDescription(rawValue);
            case "chapter" -> entity.setChapter(rawValue);
            case "protocolType" -> entity.setProtocolType(parseProtocolType(rawValue));
            default -> validateTargetField(targetField);
        }
    }

    private void validateTargetField(String targetField) {
        switch (targetField) {
            case "name", "sourceBook", "pageNumber", "category", "description", "chapter", "protocolType" -> {
                // recognized — no-op
            }
            default -> throw new IllegalStateException(
                    "Unknown targetField '" + targetField + "' in masterdata_column_config for PROTOCOL — config/importer drift");
        }
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
