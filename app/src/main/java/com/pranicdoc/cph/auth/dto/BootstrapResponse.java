package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.common.enums.Role;
import java.util.UUID;

/**
 * Bootstrap response — returns clinic + healer + tokens so admin can start immediately.
 */
public record BootstrapResponse(
        UUID clinicId,
        String clinicCode,
        UUID healerId,
        String fullName,
        String email,
        Role role,
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
