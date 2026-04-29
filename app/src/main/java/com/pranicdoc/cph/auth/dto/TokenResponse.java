package com.pranicdoc.cph.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
