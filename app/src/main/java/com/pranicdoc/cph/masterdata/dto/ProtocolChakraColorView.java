package com.pranicdoc.cph.masterdata.dto;

import com.pranicdoc.cph.common.enums.ColorUsageType;

import java.util.UUID;

/**
 * Read-model for step 3 of the protocol -> chakra -> color flow: which colors
 * (distinguishing CLEANSING vs ENERGISING) apply to a given protocol+chakra
 * combination. Deliberately a lean projection, not the {@code
 * ProtocolChakraColor}/{@code PranaColorCatalog} entity graph — callers
 * already hold the full color catalog (fetched independently via
 * {@code GET /masterdata/colors}) and only need to know which {@code colorId}
 * applies, for which usage, on which protocol+chakra pair.
 */
public record ProtocolChakraColorView(
        UUID protocolId,
        UUID chakraId,
        UUID colorId,
        ColorUsageType usageType
) {}
