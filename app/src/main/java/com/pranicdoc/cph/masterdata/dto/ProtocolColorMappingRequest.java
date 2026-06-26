package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ProtocolColorMappingRequest(
        @NotBlank String protocolName,
        @NotEmpty List<@NotNull UUID> colorIds,
        @NotNull ColorUsageType usageType,
        UUID clinicId,
        @NotBlank String chakraName
) {}
