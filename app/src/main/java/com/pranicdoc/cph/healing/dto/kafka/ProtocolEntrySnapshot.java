package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;
import java.util.UUID;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record ProtocolEntrySnapshot(
        UUID chakraId,
        String targetArea,
        String technique,
        String notes,
        List<ProtocolColorSnapshot> colors
) {}
