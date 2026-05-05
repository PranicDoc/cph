package com.pranicdoc.cph.healing.dto.kafka;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PdfJob {
    private String jobId;
    private UUID healingCaseId;
    private int retryCount;
    private Instant createdAt;
}