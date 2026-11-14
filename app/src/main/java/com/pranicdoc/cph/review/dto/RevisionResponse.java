package com.pranicdoc.cph.review.dto;

import com.pranicdoc.cph.review.model.SubmittedVia;

import java.time.Instant;
import java.util.UUID;

public record RevisionResponse(int revisionNo, UUID pdfJobId, SubmittedVia submittedVia, Instant submittedAt) {
}
