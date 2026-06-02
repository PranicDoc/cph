package com.pranicdoc.cph.healing.dto;

import java.time.Instant;

public record PreSignedUrlResponse(
        String url,
        Instant expiresAt
) {
}
