package com.pranicdoc.cph.masterdata.importer;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.repository.ChakraCatalogRepository;
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
 * MasterDataImporter implementation for CHAKRA — simplest of the four
 * importers: one row equals one chakra, no grouping/multi-row logic (unlike
 * PranaColor).
 *
 * <p>Columns are a fixed Java template, not a DB-configurable registry — the
 * masterdata_column_config table/API/registry was removed. Adding or
 * renaming a column is a code change to this class (plus the spreadsheet
 * template), not an admin action.
 */
@Service
@RequiredArgsConstructor
public class ChakraImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "Chakras";
    private static final String COL_CODE = "Code";
    private static final String COL_NAME = "Name";
    private static final String COL_CATEGORY = "Category";
    private static final String COL_LOCATION = "Location";

    private final ChakraCatalogRepository chakraRepo;
    private final SpreadsheetReader spreadsheetReader;

    @Override
    public MasterdataType kind() {
        return MasterdataType.CHAKRA;
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

            String code = trimToNull(row.get(COL_CODE));
            String name = trimToNull(row.get(COL_NAME));
            String category = trimToNull(row.get(COL_CATEGORY));
            String location = trimToNull(row.get(COL_LOCATION));

            if (code == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_CODE);
                skipped++;
                continue;
            }
            if (name == null) {
                errors.add("Row " + rowNumber + ": missing " + COL_NAME);
                skipped++;
                continue;
            }
            String normalizedCode = code.toUpperCase(Locale.ROOT);

            ChakraCatalog chakra = chakraRepo.findByCode(normalizedCode).orElse(null);
            // PD-40-04-style guard: a SYSTEM row (healerId == null) can only be
            // overwritten by SUPADM; another healer's custom row can only be
            // overwritten by that healer or SUPADM. (Note: MasterDataService's
            // single-row CRUD path currently only enforces the SYSTEM-row half of
            // this — see MASTERDATA-XXX if tracking that gap.)
            if (chakra != null && (chakra.getHealerId() == null
                    ? !isSuperAdmin
                    : !Objects.equals(chakra.getHealerId(), healerId) && !isSuperAdmin)) {
                errors.add("Row " + rowNumber + ": code '" + normalizedCode
                        + "' is owned by another party — not permitted to overwrite");
                skipped++;
                continue;
            }
            if (chakra == null) {
                chakra = new ChakraCatalog();
                // Non-SUPADM callers can never create a SYSTEM (healerId == null) row via
                // import — mirrors MasterDataService.createChakra always stamping the
                // caller's own healerId on new rows via the CRUD path.
                chakra.setHealerId(isSuperAdmin ? null : healerId);
                created++;
            } else {
                updated++;
            }
            chakra.setCode(normalizedCode);
            chakra.setName(name);
            chakra.setCategory(category);
            chakra.setLocation(location);
            chakraRepo.save(chakra);
        }

        return new ImportResult(rows.size(), created, updated, skipped, errors);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
