package com.pranicdoc.cph.masterdata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Batch request: add one or more chakras (each with its own cleansing/
 * energising colors) to a single protocol. Always stamps the caller's own
 * healerId on every created {@code ProtocolChakra} row — a healer can never
 * write a system row or another healer's row through this endpoint.
 */
public record ProtocolChakraColorAddRequest(
        @NotEmpty List<@Valid @NotNull ProtocolChakraColorEntry> entries
) {}
