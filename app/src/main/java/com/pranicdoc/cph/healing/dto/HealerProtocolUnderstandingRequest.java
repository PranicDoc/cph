package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HealerProtocolUnderstandingRequest(
        @NotEmpty
        @Valid
        List<ChakraColorSelection> chakraSelections
) {

    public record ChakraColorSelection(
            @NotBlank
            String chakra,

            @Valid
            List<ColorSelection> energisingColors,

            @Valid
            List<ColorSelection> cleansingColors,

            @Size(max = TextLimits.CHAKRA_REASON)
            String reason
    ) {}

    public record ColorSelection(
            @NotBlank
            String color,

            List<String> properties
    ) {}
}
