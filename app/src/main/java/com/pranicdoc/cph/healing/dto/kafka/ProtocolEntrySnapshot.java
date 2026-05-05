package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;
import java.util.UUID;

public record ProtocolEntrySnapshot(
        UUID chakraId,
        String targetArea,
        String technique,
        String notes,
        List<ProtocolColorSnapshot> colors
) {}
