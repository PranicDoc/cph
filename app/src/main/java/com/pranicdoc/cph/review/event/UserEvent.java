package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Set;
import java.util.UUID;

/**
 * A live event for specific signed-in users. The {@code kind} field tells the Redis relay which
 * record to rebuild; a message without one is a {@link ReviewEvent} (sent before this field existed).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", defaultImpl = ReviewEvent.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReviewEvent.class, name = "review"),
        @JsonSubTypes.Type(value = PdfReadyEvent.class, name = "pdf-ready")
})
public sealed interface UserEvent permits ReviewEvent, PdfReadyEvent {

    /** The users whose open streams receive this event, and no one else. Nulls are skipped. */
    @JsonIgnore
    Set<UUID> targetUserIds();
}
