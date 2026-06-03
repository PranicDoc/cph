package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record ChakraScanSnapshot(
        String scanType,
        String notes,
        List<ChakraReadingSnapshot> readings
) {}