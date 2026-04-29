package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.common.enums.HealingLevel;
import com.pranicdoc.cph.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HealerCreateRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password,
        String phone,
        @NotNull Role role,
        HealingLevel healingLevel,
        @NotBlank String clinicCode   // resolve to clinicId on the backend
) {}
