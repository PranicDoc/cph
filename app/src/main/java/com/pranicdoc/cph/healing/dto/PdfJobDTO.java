package com.pranicdoc.cph.healing.dto;

import java.time.Instant;
import java.util.UUID;

public record PdfJobDTO(
        UUID healingCaseId,
        String fileUrl,
        Instant completedAt
) {
}
