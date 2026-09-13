package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
import com.pranicdoc.cph.masterdata.repository.MasterdataColumnConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MasterDataImporter implementation for CHAKRA — simplest of the four
 * importers: one row equals one chakra, no grouping/multi-row logic
 * (unlike PranaColor).
 */
@Service
@RequiredArgsConstructor
public class ChakraImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "Chakras";

    private final ChakraCatalogRepository chakraRepo;
    private final SpreadsheetReader spreadsheetReader;
    private final MasterdataColumnConfigRepository columnConfigRepo;

    @Override
    public MasterdataType kind() {
        return MasterdataType.CHAKRA;
    }

    @Override
    @Transactional
    public ImportResult importFile(MultipartFile file, boolean isSuperAdmin, UUID healerId) {
        // D-17 config-integrity fail-fast: reject the whole import up front if any
        // CHAKRA column-config row points at a targetField this importer doesn't
        // know how to write — before the spreadsheet is even parsed.
        List<MasterdataColumnConfig> columns = columnConfigRepo
                .findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType.CHAKRA);
        for (MasterdataColumnConfig config : columns) {
            validateTargetField(config.getTargetField());
        }
        // D-17 fail-fast, part 2 (WR-02): also assert every targetField this importer
        // needs is actually present among the fetched columns — not just that every
        // present row is recognized. Otherwise deleting a required config row (e.g.
        // "Code") silently disables that field's required-ness check below instead of
        // failing fast up front.
        Set<String> seenTargetFields = columns.stream()
                .map(MasterdataColumnConfig::getTargetField)
                .collect(Collectors.toSet());
        for (String required : Set.of("code", "name")) {
            if (!seenTargetFields.contains(required)) {
                throw new IllegalStateException("Missing required targetField '" + required
                        + "' in masterdata_column_config for CHAKRA — config/importer drift");
            }
        }

        List<Map<String, String>> rows = spreadsheetReader.read(file, SHEET_NAME);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        int rowNumber = 1; // header is row 0 (not counted); data rows start at 1
        for (Map<String, String> row : rows) {
            rowNumber++;

            ChakraCatalog values = new ChakraCatalog();
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

                applyColumn(values, targetField, rawValue);
            }

            if (skipError != null) {
                errors.add(skipError);
                skipped++;
                continue;
            }

            ChakraCatalog chakra = chakraRepo.findByCode(values.getCode()).orElse(null);
            // PD-40-04: same row-ownership guard MasterDataService.updateChakra enforces —
            // a SYSTEM row (healerId == null) can only be overwritten by SUPADM; another
            // healer's custom row can only be overwritten by that healer or SUPADM.
            if (chakra != null && (chakra.getHealerId() == null
                    ? !isSuperAdmin
                    : !Objects.equals(chakra.getHealerId(), healerId) && !isSuperAdmin)) {
                errors.add("Row " + rowNumber + ": code '" + values.getCode()
                        + "' is owned by another party — not permitted to overwrite");
                skipped++;
                continue;
            }
            if (chakra == null) {
                chakra = values;
                // Non-SUPADM callers can never create a SYSTEM (healerId == null) row via
                // import — mirrors MasterDataService.createChakra always stamping the
                // caller's own healerId on new rows via the CRUD path.
                chakra.setHealerId(isSuperAdmin ? null : healerId);
                created++;
            } else {
                chakra.setCode(values.getCode());
                chakra.setName(values.getName());
                chakra.setCategory(values.getCategory());
                chakra.setLocation(values.getLocation());
                updated++;
            }
            chakraRepo.save(chakra);
        }

        return new ImportResult(rows.size(), created, updated, skipped, errors);
    }

    private void applyColumn(ChakraCatalog entity, String targetField, String rawValue) {
        switch (targetField) {
            case "code" -> entity.setCode(rawValue.toUpperCase(Locale.ROOT));
            case "name" -> entity.setName(rawValue);
            case "category" -> entity.setCategory(rawValue);
            case "location" -> entity.setLocation(rawValue);
            default -> validateTargetField(targetField);
        }
    }

    private void validateTargetField(String targetField) {
        switch (targetField) {
            case "code", "name", "category", "location" -> {
                // recognized — no-op
            }
            default -> throw new IllegalStateException(
                    "Unknown targetField '" + targetField + "' in masterdata_column_config for CHAKRA — config/importer drift");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
