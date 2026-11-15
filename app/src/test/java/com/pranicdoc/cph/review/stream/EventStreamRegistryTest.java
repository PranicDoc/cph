package com.pranicdoc.cph.review.stream;

import com.pranicdoc.cph.review.event.PdfReadyEvent;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventType;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventStreamRegistryTest {

    /** Records what would go on the wire instead of needing a servlet response. */
    static class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean broken;

        Runnable timeoutCallback;
        boolean completed;

        RecordingEmitter() {
            super(EventStreamRegistry.EMITTER_TIMEOUT_MS);
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            timeoutCallback = callback;
            super.onTimeout(callback);
        }

        @Override
        public synchronized void complete() {
            completed = true;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("Broken pipe");
            }
            StringBuilder wire = new StringBuilder();
            builder.build().forEach(part -> wire.append(part.getData()));
            sent.add(wire.toString());
        }
    }

    private final List<RecordingEmitter> created = new ArrayList<>();
    private final EventStreamRegistry registry = new EventStreamRegistry(() -> {
        RecordingEmitter e = new RecordingEmitter();
        created.add(e);
        return e;
    });

    @Test
    void deliver_sendsOnlyToParties() {
        UUID healer = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        registry.open(healer);
        registry.open(reviewer);
        registry.open(bystander);
        UUID submissionId = UUID.randomUUID();

        registry.deliver(new ReviewEvent(submissionId, healer, reviewer, ReviewEventType.REVISION_ADDED, 2));

        assertThat(created.get(0).sent).hasSize(1);
        assertThat(created.get(1).sent).hasSize(1);
        assertThat(created.get(2).sent).isEmpty();
        assertThat(created.get(0).sent.get(0))
                .contains("event:review")
                .contains("{\"submissionId\":\"" + submissionId + "\",\"type\":\"REVISION_ADDED\",\"revisionNo\":2}")
                .doesNotContain(healer.toString())
                .doesNotContain(reviewer.toString());
    }

    @Test
    void deliver_withNoReviewer_reachesTheHealerAndSkipsTheNullTarget() {
        UUID healer = UUID.randomUUID();
        registry.open(healer);

        registry.deliver(new ReviewEvent(UUID.randomUUID(), healer, null, ReviewEventType.STATUS_CHANGED, 1));

        assertThat(created.get(0).sent).hasSize(1);
    }

    @Test
    void deadEmitterRemovedOnIOException() {
        UUID healer = UUID.randomUUID();
        registry.open(healer);
        registry.open(healer);
        created.get(0).broken = true;
        ReviewEvent event = new ReviewEvent(UUID.randomUUID(), healer, UUID.randomUUID(),
                ReviewEventType.REVISION_ADDED, 1);

        registry.deliver(event);

        assertThat(registry.openCount(healer)).isEqualTo(1);
        assertThat(created.get(1).sent).hasSize(1);

        registry.deliver(event);
        assertThat(created.get(1).sent).hasSize(2);
    }

    @Test
    void heartbeat_sendsCommentToEveryEmitterAndDropsDeadOnes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        registry.open(a);
        registry.open(b);
        created.get(1).broken = true;

        registry.heartbeat();

        assertThat(created.get(0).sent).containsExactly(":hb\n\n");
        assertThat(registry.openCount(b)).isZero();
    }

    @Test
    void timeoutRemovesAndCompletesTheEmitter() {
        UUID healer = UUID.randomUUID();
        registry.open(healer);
        RecordingEmitter emitter = created.get(0);

        emitter.timeoutCallback.run();

        assertThat(emitter.completed).isTrue();
        assertThat(registry.openCount(healer)).isZero();
    }

    @Test
    void sameUserOnTwoInstancesBothReceive() {
        UUID healer = UUID.randomUUID();
        registry.open(healer);
        registry.open(healer);

        registry.deliver(new ReviewEvent(UUID.randomUUID(), healer, UUID.randomUUID(),
                ReviewEventType.STATUS_CHANGED, null));

        assertThat(created).allSatisfy(e -> assertThat(e.sent).hasSize(1));
    }

    @Test
    void pdfReady_deliveredOnlyToTheCaseHealer() {
        UUID healer = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        registry.open(healer);
        registry.open(reviewer);
        registry.open(bystander);
        UUID caseId = UUID.randomUUID();

        registry.deliver(new PdfReadyEvent(healer, caseId, "job-9"));

        assertThat(created.get(0).sent).hasSize(1);
        assertThat(created.get(1).sent).isEmpty();
        assertThat(created.get(2).sent).isEmpty();
        assertThat(created.get(0).sent.get(0))
                .contains("event:pdf-ready")
                .contains("{\"caseId\":\"" + caseId + "\",\"jobId\":\"job-9\"}")
                .doesNotContain(healer.toString());
    }
}
