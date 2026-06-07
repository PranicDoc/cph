package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record InitFeedbackSessionRequest(@NotBlank String token) {}
