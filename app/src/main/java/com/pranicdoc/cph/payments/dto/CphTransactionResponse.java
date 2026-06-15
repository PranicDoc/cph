package com.pranicdoc.cph.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CphTransactionResponse(
        UUID id,
        String type,
        int amount,
        @JsonProperty("balance_after") int balanceAfter,
        @JsonProperty("reference_id") String referenceId,
        String notes,
        @JsonProperty("created_at") Instant createdAt
) {}
