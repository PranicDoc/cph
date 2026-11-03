package com.pranicdoc.cph.patient.dto;

import com.pranicdoc.cph.common.enums.Gender;
import com.pranicdoc.cph.common.enums.MaritalStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A field left out (null) is not changed; an empty text clears an optional field. The rules match
 * registration, so an edit cannot save what registration would have refused.
 */
public record PatientUpdateRequest(
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Past LocalDate dob,
        Gender gender,
        MaritalStatus maritalStatus,
        @Email String email,
        @Pattern(regexp = "^\\+?[0-9]{10,15}$") String contactWhatsapp,
        String occupation,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 255) String city,
        @Size(max = 255) String state,
        @Pattern(regexp = "^([0-9]{5,10})?$") String zip
) {}
