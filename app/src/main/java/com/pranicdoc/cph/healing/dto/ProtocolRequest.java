package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import com.pranicdoc.cph.common.enums.ColorUsageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ProtocolRequest(
        UUID chakraId,
        @Size(max = TextLimits.NOTE, message = "Target area can be at most {max} characters") String targetArea,
        @Size(max = TextLimits.NOTE, message = "Technique can be at most {max} characters") String technique,
        @Size(max = TextLimits.NOTE, message = "Notes can be at most {max} characters") String notes,
        List<ProtocolColorRequest> colors,
        String protocolName,
        String referenceBook,
        String detailedProtocol
) {
    public record ProtocolColorRequest(
            UUID colorId,
            ColorUsageType usageType
    ) {}
}
