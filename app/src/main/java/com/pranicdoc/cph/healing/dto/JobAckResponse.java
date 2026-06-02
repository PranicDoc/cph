package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.JobStatus;

public record JobAckResponse(
    String jobId,
    JobStatus status
)
{
}
