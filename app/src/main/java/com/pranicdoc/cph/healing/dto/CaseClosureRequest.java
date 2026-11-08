package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.validation.TextLimits;
import jakarta.validation.constraints.Size;
public record CaseClosureRequest(
        @Size(max = TextLimits.CLOSURE_COMMENTS, message = "Comments can be at most {max} characters") String additionalComments,
        boolean isHealingContinued
) {}
