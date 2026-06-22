package com.pranicdoc.cph.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PranaColorRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String colorHex,
        String properties,
        UUID clinicId
) {}
