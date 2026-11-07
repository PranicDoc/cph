package com.pranicdoc.cph.payments.dto;

import java.util.UUID;

/** {@code credits} null means credit-service deducts its configured credits_per_pdf. */
public record DeductRequest(UUID healerId, Integer credits) {}
