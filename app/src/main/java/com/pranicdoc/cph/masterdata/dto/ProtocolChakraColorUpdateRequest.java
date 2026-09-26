package com.pranicdoc.cph.masterdata.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replaces the full cleansing/energising color set for one existing
 * protocol+chakra link. Full replace, not a patch/merge — simplest correct
 * semantics for the small lists this UI works with.
 */
public record ProtocolChakraColorUpdateRequest(
        List<@NotNull UUID> cleansingColorIds,
        List<@NotNull UUID> energisingColorIds
) {}
