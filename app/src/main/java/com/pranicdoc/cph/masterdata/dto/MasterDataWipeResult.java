package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.MasterDataType;

/** Counts of hard-deleted rows per category — categories not covered by the requested {@link MasterDataType} stay 0. */
public record MasterDataWipeResult(
        MasterDataType type,
        int chakrasDeleted,
        int colorsDeleted,
        int mappingsDeleted,
        int protocolsDeleted
) {}
