package com.pranicdoc.cph.healing.dto;

import java.util.List;

public record HealingDetailsRequest(
    String protocolName,
    String bookReference,
    List<TreatmentStepRequest> steps
) {
}
