package com.pranicdoc.cph.masterdata.dto;

import java.util.List;

/**
 * Shared 5-field import result DTO used by every {@code MasterDataImporter}
 * implementation (Phase 02), including {@code ProtocolImportService} since
 * Plan 02-03, which superseded and deleted the former Protocol-specific
 * {@code ProtocolImportResult} DTO.
 */
public record ImportResult(
        int totalRows,
        int created,
        int updated,
        int skipped,
        List<String> errors
) {}
