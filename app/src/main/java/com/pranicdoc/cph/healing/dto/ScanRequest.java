package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import com.pranicdoc.cph.common.enums.ScanType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ScanRequest(
        @NotNull ScanType scanType,
        @Size(max = TextLimits.NOTE, message = "Notes can be at most {max} characters") String notes,
        @NotNull @Valid List<ChakraReadingRequest> readings
) {
    public record ChakraReadingRequest(
            @NotNull UUID chakraId,
            @NotNull String chakraName,
            String energyLevel,
            String activationLevel,
            @Size(max = TextLimits.NOTE, message = "Notes can be at most {max} characters") String notes
    ) {}
}
