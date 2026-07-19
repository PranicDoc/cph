package com.pranicdoc.cph.healing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

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
            List<ColorSelection> cleansingColors
    ) {}

    public record ColorSelection(
            @NotBlank
            String color,

            List<String> properties
    ) {}
}
