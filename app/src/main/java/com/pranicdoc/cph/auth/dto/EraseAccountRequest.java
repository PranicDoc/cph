package com.pranicdoc.cph.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code confirmation} must be the account's e-mail address, typed out. */
public record EraseAccountRequest(@NotBlank String confirmation) {}
