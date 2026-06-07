package com.pranicdoc.cph.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AccountResponse(
        @JsonProperty("account_id") UUID accountId,
        @JsonProperty("owner_type") String ownerType,
        @JsonProperty("owner_id") UUID ownerId,
        int balance
) {}
