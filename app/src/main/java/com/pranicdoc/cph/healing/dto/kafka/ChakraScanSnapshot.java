package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;

public record ChakraScanSnapshot(
        String scanType,
        String notes,
        List<ChakraReadingSnapshot> readings
) {}