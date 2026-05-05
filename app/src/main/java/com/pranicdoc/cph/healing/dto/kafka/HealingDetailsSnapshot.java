package com.pranicdoc.cph.healing.dto.kafka;

import java.util.List;

public record HealingDetailsSnapshot(
        String protocolName,
        String bookReference,
        List<TreatmentStepSnapshot> treatmentSteps
) {}