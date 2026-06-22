package com.pranicdoc.cph.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ChakraRequest(
        @NotBlank String name,
        @NotBlank String code,
        String category,
        String location,
        UUID clinicId
) {}
