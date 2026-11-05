package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
public record DeclarationRequest(
        boolean patientConsent,
        boolean healerDeclaration,
        @Size(max = TextLimits.NOTE, message = "Signature can be at most {max} characters") String patientSignature,
        @Size(max = TextLimits.NOTE, message = "Signature can be at most {max} characters") String healerSignature,
        String signedAt
) {}
