package com.pranicdoc.cph.masterdata.dto;

import java.util.List;
import java.util.UUID;

/**
 * One protocol+chakra link, grouped with its colors, for the single-protocol
 * "manage chakras/colors" admin view (distinct from {@link ProtocolChakraColorView},
 * which is a flat per-color-row projection used by the healing-session wizard).
 *
 * <p>{@code healerId} null means this is a system row: read-only for anyone
 * without the SUPADM system-row override (mirrors every other master-data
 * entity's {@code getHealerId() == null} check). Non-null means the row
 * belongs to that healer and is editable/deletable by them alone (or SUPADM).
 */
public record ProtocolChakraColorGroup(
        UUID protocolChakraId,
        UUID chakraId,
        String chakraName,
        UUID healerId,
        List<UUID> cleansingColorIds,
        List<UUID> energisingColorIds
) {}
