package com.pranicdoc.cph.masterdata.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * One chakra + its cleansing/energising color sets, as submitted when a
 * healer adds chakras/colors to a single protocol. Either color list may be
 * empty (a chakra can be linked with no colors yet), but not both null.
 */
public record ProtocolChakraColorEntry(
        @NotNull UUID chakraId,
        List<@NotNull UUID> cleansingColorIds,
        List<@NotNull UUID> energisingColorIds
) {}
