package com.pranicdoc.cph.auth.dto;

import java.time.LocalDate;

/** What the patient feedback form shows alongside the link: the date of the healing being rated. */
public record FeedbackSessionResponse(LocalDate healingDate) {}
