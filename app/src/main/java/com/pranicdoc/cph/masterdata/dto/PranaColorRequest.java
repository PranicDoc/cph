package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PranaColorRequest(
        @NotBlank @Size(max = TextLimits.SHORT_TEXT, message = "Name can be at most {max} characters") String name,
        @NotBlank @Size(max = TextLimits.COLOR_CODE, message = "Code can be at most {max} characters") String code,
        @NotBlank @Size(max = TextLimits.COLOR_CODE, message = "Colour can be at most {max} characters") String colorHex,
        List<String> properties,
        @NotNull ColorUsageType category,
        UUID healerId
) {}
