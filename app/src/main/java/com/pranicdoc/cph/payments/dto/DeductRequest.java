package com.pranicdoc.cph.payments.dto;

import java.util.UUID;

// CROSS-SERVICE BOUNDARY (Phase 27): creditService still keys on a clinic-shaped ownerId;
// clinicId here is sourced from healer.clinicId bridge until Phase 27 migrates accounts to
// OwnerType.HEALER. Do NOT remove.
public record DeductRequest(UUID healerId, UUID clinicId) {}
