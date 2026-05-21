package com.pranicdoc.cph.healing.dto;

public record CaseClosureRequest(
        String additionalComments,
        boolean isHealingContinued
) {}
