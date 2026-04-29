package com.pranicdoc.cph.patient.dto;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.enums.Gender;
import com.pranicdoc.cph.common.enums.MaritalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientCaseResponse (
       UUID id,
       String firstName,
       String lastName,
       LocalDate dob,
       Gender gender,
       MaritalStatus maritalStatus,
       String email,
       String contact,
       String occupation,
       String addressLine1,
       String addressLine2,
       String city,
       String state,
       String zip,
       Instant createdAt,
       Instant updatedAt,
       CaseStatus status,
       UUID healingCaseId
) {}
