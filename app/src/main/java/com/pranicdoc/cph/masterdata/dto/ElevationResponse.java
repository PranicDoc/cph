package com.pranicdoc.cph.masterdata.dto;

import java.time.Instant;

/** Which master-data type is unlocked, and until when. */
public record ElevationResponse(String resource, Instant expiresAt) {
}
