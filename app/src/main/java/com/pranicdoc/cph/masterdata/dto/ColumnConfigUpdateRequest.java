package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Update-column-config request body.
 *
 * {@code masterdataType} is deliberately absent — immutable after create,
 * matching {@code ConsentType.key}'s immutability precedent. {@code
 * systemDefined} is also absent — never client-settable, on create or
 * update. {@code MasterdataColumnConfigService.update()} never mutates
 * either field.
 */
public record ColumnConfigUpdateRequest(
        @NotBlank @Size(max = 120) String columnName,
        @NotBlank @Size(max = 60) String targetField,
        boolean required,
        @NotNull ColumnDataType dataType
) {}
