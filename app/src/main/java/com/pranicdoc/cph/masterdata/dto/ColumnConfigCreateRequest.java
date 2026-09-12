package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create-column-config request body (T-01-04 mitigation).
 *
 * Deliberately carries ONLY {@code masterdataType}, {@code columnName},
 * {@code targetField}, {@code required}, and {@code dataType}.
 * {@code systemDefined} is structurally absent from this record — the
 * caller is physically unable to supply it, even by crafting extra JSON
 * fields. {@code MasterdataColumnConfigService.create()} hardcodes
 * {@code systemDefined = false} on every admin-created row.
 */
public record ColumnConfigCreateRequest(
        @NotNull MasterdataType masterdataType,
        @NotBlank @Size(max = 120) String columnName,
        @NotBlank @Size(max = 60) String targetField,
        boolean required,
        @NotNull ColumnDataType dataType
) {}
