package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;

@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public record HealingDetailsSnapshot(
        String protocolName,
        String bookReference,
        List<TreatmentStepSnapshot> treatmentSteps
) {}