package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.MasterDataImporter;
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
 */
@Service
@RequiredArgsConstructor
public class ProtocolImportService implements MasterDataImporter {

    private static final String SHEET_NAME = "Protocols";
    private static final String COL_NAME = "Protocol/Technique Name";
    private static final String COL_BOOK = "Book";
    private static final String COL_PAGE = "Page";
    private static final String COL_CATEGORY = "Category";
    private static final String COL_DESCRIPTION = "Description";
    private static final String COL_CHAPTER = "Chapter";
    private static final String COL_TYPE = "Type";

    private final ProtocolCatalogRepository protocolRepo;
    private final SpreadsheetReader spreadsheetReader;

    @Override
    public MasterdataType kind() {
        return MasterdataType.PROTOCOL;
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
