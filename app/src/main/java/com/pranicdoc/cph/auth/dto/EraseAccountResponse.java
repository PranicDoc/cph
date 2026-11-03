package com.pranicdoc.cph.auth.dto;

/** {@code creditAccountErased} is false when the credit service could not be reached (the rest is done). */
public record EraseAccountResponse(boolean creditAccountErased) {}
