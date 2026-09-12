package com.pranicdoc.cph.masterdata.dto;

import java.util.List;

/**
 * Shared 5-field import result DTO used by every {@code MasterDataImporter}
 * implementation (Phase 02). Byte-identical field set to {@link
 * ProtocolImportResult}, under a new name so new code (registry, controller,
 * all four importers) does not depend on the Protocol-specific type.
 * {@link ProtocolImportResult} itself is untouched by this plan.
 */
public record ImportResult(
        int totalRows,
        int created,
        int updated,
        int skipped,
        List<String> errors
) {}
