package com.pranicdoc.cph.healing.dto;

public record DeclarationRequest(
        boolean patientConsent,
        boolean healerDeclaration,
        String patientSignature,
        String healerSignature,
        String signedAt
) {}
