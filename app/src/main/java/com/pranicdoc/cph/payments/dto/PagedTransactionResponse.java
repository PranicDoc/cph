package com.pranicdoc.cph.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PagedTransactionResponse(
        List<CphTransactionResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean last
) {}
