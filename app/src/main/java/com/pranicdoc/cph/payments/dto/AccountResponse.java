package com.pranicdoc.cph.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountResponse(
        @JsonProperty("account_id") UUID accountId,
        @JsonProperty("owner_type") String ownerType,
        @JsonProperty("owner_id") UUID ownerId,
        @JsonProperty("free_balance") int freeBalance,
        @JsonProperty("paid_balance") int paidBalance,
        @JsonProperty("plan_tier") String planTier,
        @JsonProperty("plan_expires_at") java.time.Instant planExpiresAt
) {}
