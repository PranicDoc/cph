package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PranaColorRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String colorHex,
        List<String> properties,
        @NotNull ColorUsageType category,
        UUID healerId
) {}
