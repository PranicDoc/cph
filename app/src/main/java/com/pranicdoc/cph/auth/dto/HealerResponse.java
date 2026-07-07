package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.common.enums.HealingLevel;
import com.pranicdoc.cph.common.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record HealerResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        Role role,
        HealingLevel healingLevel,
        boolean active,
        UUID clinicId,
        String clinicCode,
        Instant createdAt,
        boolean hasPassword
) {}
