package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.ImportJobStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-model for one protocol import job — the "Imports" tab's list/status view. */
public record ProtocolImportJobView(
        UUID id,
        String originalFilename,
        ImportJobStatus status,
        Integer totalRows,
        Integer created,
        Integer updated,
        Integer skipped,
        List<String> errors,
        String failureMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {}
