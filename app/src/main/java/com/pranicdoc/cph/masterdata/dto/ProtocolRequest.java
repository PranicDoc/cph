package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.ProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProtocolRequest(
        @NotBlank String name,
        @NotBlank String sourceBook,
        @NotNull Integer pageNumber,
        String category,
        String description,
        String chapter,
        @NotNull ProtocolType protocolType,
        UUID healerId
) {}
