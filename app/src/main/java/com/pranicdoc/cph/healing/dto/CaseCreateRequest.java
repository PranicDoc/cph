package com.pranicdoc.cph.healing.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CaseCreateRequest(
        @NotNull UUID patientId,
        @NotNull DeclarationRequest declaration
) {}