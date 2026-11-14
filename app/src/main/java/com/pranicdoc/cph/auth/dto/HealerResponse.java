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
        int nextNameChangeCost,
        boolean autoSubmitForReview,
        /** Whether an instructor is linked to this healer; only filled on the healer's own profile reads. */
        boolean reviewerLinked
) {
    public static HealerResponse from(Healer h) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(), h.getCreatedAt(),
                h.getPasswordHash() != null, h.getProviderUserId() != null,
                h.getUiVersion(), h.nextNameChangeCost(), h.isAutoSubmitForReview(), false
        );
    }

    public HealerResponse withReviewerLinked(boolean linked) {
        return new HealerResponse(id, fullName, email, phone, role, healingLevel, active, createdAt, hasPassword,
                hasGoogleLinked, uiVersion, nextNameChangeCost, autoSubmitForReview, linked);
    }
}
