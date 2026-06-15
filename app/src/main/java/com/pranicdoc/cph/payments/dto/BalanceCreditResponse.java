package com.pranicdoc.cph.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceCreditResponse(
        UUID ownerId,
        String ownerType,
        int balance,
        @JsonProperty("payg_enabled") boolean paygEnabled,
        @JsonProperty("low_balance") boolean lowBalance
) {}
