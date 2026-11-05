package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HealingDetailsRequest(
    @Size(max = TextLimits.NOTE, message = "Protocol name can be at most {max} characters") String protocolName,
    @Size(max = TextLimits.NOTE, message = "Book reference can be at most {max} characters") String bookReference,
    @Size(max = TextLimits.MEDIUM_BOX, message = "Healing details can be at most {max} characters") String detailedProtocol,
    @jakarta.validation.Valid List<TreatmentStepRequest> treatmentSteps
) {
}
