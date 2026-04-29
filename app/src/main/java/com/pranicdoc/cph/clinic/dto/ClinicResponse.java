package com.pranicdoc.cph.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public record ClinicResponse(
        UUID id,
        String code,
        String name,
        String phone,
        String email,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zip,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
