package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;
import java.util.UUID;

/** A PDF job for one of the healer's cases has finished. Delivered only to that healer. */
public record PdfReadyEvent(UUID healerId, UUID caseId, String jobId) implements UserEvent {

    @Override
    @JsonIgnore
    public Set<UUID> targetUserIds() {
        return healerId == null ? Set.of() : Set.of(healerId);
    }
}
