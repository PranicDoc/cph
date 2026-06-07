package com.pranicdoc.cph.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeductResponse(String result, @JsonProperty("balance_after") int balanceAfter) {}
