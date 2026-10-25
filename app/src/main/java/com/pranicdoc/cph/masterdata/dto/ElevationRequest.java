package com.pranicdoc.cph.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

/** The user's password, confirming an "unlock shared entries". */
public record ElevationRequest(@NotBlank String password) {
}
