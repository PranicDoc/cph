package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.ScanType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ScanRequest(
        @NotNull ScanType scanType,
        String notes,
        @NotNull List<ChakraReadingRequest> readings
) {
    public record ChakraReadingRequest(
            @NotNull UUID chakraId,
            @NotNull String chakraName,
            String energyLevel,
            String activationLevel,
            String notes
    ) {}
}
