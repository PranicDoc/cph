package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.model.ColumnDataType;

import java.time.Instant;
import java.util.UUID;

/**
 * View of a MasterdataColumnConfig registry row.
 */
public record ColumnConfigResponse(
        UUID id,
        MasterdataType masterdataType,
        String columnName,
        String targetField,
        boolean required,
        ColumnDataType dataType,
        boolean systemDefined,
        Instant createdAt
) {}
