package com.pranicdoc.cph.review.stream;

import com.pranicdoc.cph.common.feature.RequiresCaseReview;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live events for the signed-in user (review updates, "PDF ready"). Open to every authenticated healer
 * with no capability gate: the stream is keyed to the caller's own id and carries only events meant
 * for them: submissions they are the healer or reviewer of, and PDFs of their own cases. Patient
 * tokens are refused in SecurityConfig.
 */
@RestController
@RequiresCaseReview
@RequiredArgsConstructor
public class EventStreamController {

    private final EventStreamRegistry registry;
    private final SecurityContextUtil securityContextUtil;

    @GetMapping(value = "/api/v1/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        SseEmitter emitter = registry.open(securityContextUtil.getCurrentHealerId());
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}
