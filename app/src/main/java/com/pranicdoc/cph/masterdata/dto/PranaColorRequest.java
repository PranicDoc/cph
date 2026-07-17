package com.pranicdoc.cph.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record PranaColorRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String colorHex,
        List<String> properties,
        UUID clinicId
) {}
