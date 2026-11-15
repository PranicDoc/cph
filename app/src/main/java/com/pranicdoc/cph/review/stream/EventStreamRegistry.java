package com.pranicdoc.cph.review.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.review.event.PdfReadyEvent;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The event streams open on this instance, by user. A review event goes only to the streams of the
 * submission's healer and reviewer, carrying just the submission id, type and revision number
 * ({@code event: review}); a PDF-ready event goes only to the case's healer, carrying the case and
 * job ids ({@code event: pdf-ready}).
 * A stream whose connection has gone is dropped on the first failed send.
 */
@Slf4j
@Component
public class EventStreamRegistry {

    static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;
    static final String REVIEW_EVENT_NAME = "review";
    static final String PDF_READY_EVENT_NAME = "pdf-ready";
    /** Tabs per user; opening one more closes the oldest, so one account cannot hold unbounded connections. */
    static final int MAX_STREAMS_PER_USER = 5;

    /** The browser-facing payload: no party ids. */
    record StreamPayload(UUID submissionId, ReviewEventType type, Integer revisionNo) {
    }

    /** The browser-facing payload of a PDF-ready event. */
    record PdfReadyPayload(UUID caseId, String jobId) {
    }

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> byUser = new ConcurrentHashMap<>();
    private final Supplier<SseEmitter> emitterFactory;
    private final ObjectMapper json = new ObjectMapper();

    public EventStreamRegistry() {
        this(() -> new SseEmitter(EMITTER_TIMEOUT_MS));
    }

    EventStreamRegistry(Supplier<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter open(UUID userId) {
        SseEmitter emitter = emitterFactory.get();
        CopyOnWriteArrayList<SseEmitter> streams = byUser.compute(userId, (id, existing) -> {
            CopyOnWriteArrayList<SseEmitter> list = existing != null ? existing : new CopyOnWriteArrayList<>();
            list.add(emitter);
            return list;
        });
        while (streams.size() > MAX_STREAMS_PER_USER) {
            SseEmitter oldest = streams.remove(0);
            completeQuietly(oldest);
        }
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            // Ends the async request cleanly; the browser's EventSource then reconnects by itself.
            completeQuietly(emitter);
        });
        emitter.onError(e -> remove(userId, emitter));
        return emitter;
    }

    /** Sends the event to its target users' open streams, and to no one else. */
    public void deliver(UserEvent event) {
        String name;
        String data;
        try {
            if (event instanceof ReviewEvent review) {
                name = REVIEW_EVENT_NAME;
                data = json.writeValueAsString(
                        new StreamPayload(review.submissionId(), review.type(), review.revisionNo()));
            } else if (event instanceof PdfReadyEvent pdf) {
                name = PDF_READY_EVENT_NAME;
                data = json.writeValueAsString(new PdfReadyPayload(pdf.caseId(), pdf.jobId()));
            } else {
                return;
            }
        } catch (JsonProcessingException e) {
            log.warn("Event {} not serialisable: {}", event.getClass().getSimpleName(), e.getMessage());
            return;
        }
        for (UUID userId : event.targetUserIds()) {
            for (SseEmitter emitter : streamsOf(userId)) {
                send(userId, emitter, SseEmitter.event().name(name).data(data));
            }
        }
    }

    /** Keep-alive only: a {@code :hb} comment every 25 s so proxies keep idle streams open. Fetches nothing. */
    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        byUser.forEach((userId, streams) -> {
            for (SseEmitter emitter : streams) {
                send(userId, emitter, SseEmitter.event().comment("hb"));
            }
        });
    }

    int openCount(UUID userId) {
        return streamsOf(userId).size();
    }

    private List<SseEmitter> streamsOf(UUID userId) {
        CopyOnWriteArrayList<SseEmitter> streams = byUser.get(userId);
        return streams == null ? List.of() : streams;
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException deadConnection) {
            remove(userId, emitter);
            completeQuietly(emitter);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        byUser.computeIfPresent(userId, (id, streams) -> {
            streams.remove(emitter);
            return streams.isEmpty() ? null : streams;
        });
    }

    private static void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // The connection is already gone.
        }
    }
}
