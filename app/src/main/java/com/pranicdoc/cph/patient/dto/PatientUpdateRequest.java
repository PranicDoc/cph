package com.pranicdoc.cph.patient.dto;

import com.pranicdoc.cph.common.enums.Gender;
import com.pranicdoc.cph.common.enums.MaritalStatus;

import java.time.LocalDate;

public record PatientUpdateRequest(
        String firstName,
        String lastName,
        LocalDate dob,
        Gender gender,
        MaritalStatus maritalStatus,
        String email,
        String contactWhatsapp,
        String occupation,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zip
) {}
