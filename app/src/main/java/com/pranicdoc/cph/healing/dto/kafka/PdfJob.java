package com.pranicdoc.cph.healing.dto.kafka;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public class PdfJob {
    private String jobId;
    private UUID healingCaseId;
    private int retryCount;
    private Instant createdAt;
}