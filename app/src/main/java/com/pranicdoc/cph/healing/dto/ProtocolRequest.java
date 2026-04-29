package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.ColorUsageType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ProtocolRequest(
        UUID chakraId,
        String targetArea,
        String technique,
        String notes,
        @NotNull List<ProtocolColorRequest> colors
) {
    public record ProtocolColorRequest(
            @NotNull UUID colorId,
            @NotNull ColorUsageType usageType
    ) {}
}
