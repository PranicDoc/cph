package com.pranicdoc.cph.consent.dto;

/**
 * Close-reading-session request body (READ-01/READ-02 / T-33-01 mitigation).
 *
 * closedAt and durationSeconds are structurally absent because both are
 * computed server-side from server-recorded clock values (T-33-01) — never
 * accepted as client-supplied fields, which would let a client fabricate a
 * longer reading time.
 *
 * scrollCompleted is a deliberately nullable, unvalidated boxed Boolean: the
 * navigator.sendBeacon() abandon path may deliver an empty or partial body,
 * and the service treats a null value as false rather than rejecting the
 * request.
 */
public record ReadingSessionCloseRequest(Boolean scrollCompleted) {}
