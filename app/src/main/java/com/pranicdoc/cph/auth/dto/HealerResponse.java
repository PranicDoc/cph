package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.common.enums.HealingLevel;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.enums.UiVersion;

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
        Instant createdAt,
        boolean hasPassword,
        boolean hasGoogleLinked,
        UiVersion uiVersion,
        int nextNameChangeCost
) {
    public static HealerResponse from(Healer h) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(), h.getCreatedAt(),
                h.getPasswordHash() != null, h.getProviderUserId() != null,
                h.getUiVersion(), h.nextNameChangeCost()
        );
    }
}
