package com.pranicdoc.cph.healing.dto.kafka;

import java.util.UUID;

public record ProtocolColorSnapshot(
        UUID colorId,
        String usageType
) {}