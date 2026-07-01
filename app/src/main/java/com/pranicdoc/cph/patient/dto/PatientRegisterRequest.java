package com.pranicdoc.cph.patient.dto;

import com.pranicdoc.cph.healing.dto.DeclarationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PatientRegisterRequest(
        @Valid @NotNull PatientCreateRequest patient,
        @Valid @NotNull DeclarationRequest declaration
) {}
