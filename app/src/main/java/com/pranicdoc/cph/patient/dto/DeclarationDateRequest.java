package com.pranicdoc.cph.patient.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DeclarationDateRequest(@NotNull LocalDate declarationDate) {}
