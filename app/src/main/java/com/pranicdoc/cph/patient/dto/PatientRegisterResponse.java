package com.pranicdoc.cph.patient.dto;

import java.util.UUID;

public record PatientRegisterResponse(
        UUID patientId,
        UUID caseId,
        String firstName,
        String lastName
) {}
