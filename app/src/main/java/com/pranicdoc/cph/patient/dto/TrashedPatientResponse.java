package com.pranicdoc.cph.patient.dto;

import java.time.Instant;
import java.util.UUID;

/** A patient in the trash: who, whose, when it was deleted and when it will be erased for good. */
public record TrashedPatientResponse(
        UUID id,
        String patientCode,
        String firstName,
        String lastName,
        UUID healerId,
        String healerName,
        Instant deletedAt,
        Instant purgeAt,
        int caseCount
) {}
