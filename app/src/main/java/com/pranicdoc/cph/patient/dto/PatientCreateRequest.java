package com.pranicdoc.cph.patient.dto;

import com.pranicdoc.cph.common.enums.Gender;
import com.pranicdoc.cph.common.enums.MaritalStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record PatientCreateRequest(
        @NotBlank @Size(max = 50)
        String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @Past LocalDate dob,
        Gender gender,
        MaritalStatus maritalStatus,
        @Email String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$") String contact,
        String occupation,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 255) String city,
        @Size(max = 255) String state,
        @Pattern(regexp = "^[0-9]{5,10}$") String zip,
        MedicalHistoryRequest medicalHistory
) {}
