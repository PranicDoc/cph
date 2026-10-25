package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PranaColorRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String colorHex,
        List<String> properties,
        @NotEmpty Set<ColorUsageType> categories,
        UUID healerId
) {}
