package com.pranicdoc.cph.healing.dto;

import java.util.UUID;

public record PdfJobDTO(
        UUID healingCaseId,
        String fileUrl
) {
}
