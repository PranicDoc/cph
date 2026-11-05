package com.pranicdoc.cph.patient.dto;

import java.time.LocalDate;

/**
 * The declaration date of the patient's latest case.
 *
 * @param exists   whether that case has a declaration at all
 * @param editable false when there is none, or while the case is locked
 */
public record DeclarationDateResponse(LocalDate declarationDate, boolean exists, boolean editable) {}
