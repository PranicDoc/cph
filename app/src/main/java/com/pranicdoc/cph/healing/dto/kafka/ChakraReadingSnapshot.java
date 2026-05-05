package com.pranicdoc.cph.healing.dto.kafka;

import java.util.UUID;

public record ChakraReadingSnapshot(
        UUID chakraId,
        String sizeReading,
        String conditionReading,
        String notes
) {}