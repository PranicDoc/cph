package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Accept-invite activation payload (D-08). Deliberately NO role/clinic/email
 * fields — those were fixed by the admin (or system, for self-signup) at
 * invite time and are not re-confirmable by the invited person. `phone` is
 * optional (D-02, no validation annotation) — matches Healer.phone, which is
 * unconstrained.
 */
public record AcceptInviteRequest(
        @NotBlank String fullName,
        @NotBlank @Size(min = 6) String password,
        String phone
) {}
