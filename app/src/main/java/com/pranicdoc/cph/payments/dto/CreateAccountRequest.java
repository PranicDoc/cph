package com.pranicdoc.cph.payments.dto;

import java.util.UUID;

public record CreateAccountRequest(String ownerType, UUID ownerId) {}
