import { useCallback, useContext, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import api, { refreshSession } from "../lib/axios";
import { ReviewStreamContext } from "./reviewStreamContext";
import {
  APPROVED_COUNT_KEY,
  CASE_STATUSES_KEY,
  MY_SUBMISSIONS_KEY,
  REVIEW_INBOX_KEY,
  REVIEW_KEY,
  REVIEW_STATUSES_KEY,
  reviewSubmissionKey,
} from "../lib/reviewQueryKeys";

/** Same base path as every other API call, so it works through the Vite proxy and behind nginx. */
export const REVIEW_STREAM_URL = `${api.defaults.baseURL ?? ""}/api/v1/reviews/stream`;

export type ReviewStreamEventType = "REVISION_ADDED" | "COMMENT_ADDED" | "STATUS_CHANGED";

/** The `data` of one `event: review` message. */
export interface ReviewStreamEvent {
  submissionId: string;
  type: ReviewStreamEventType;
  revisionNo: number | null;
}

/** Refresh-and-reconnect attempts in a row that never reach an open stream before giving up. */
const MAX_RECONNECTS_WITHOUT_OPEN = 3;

/**
 * The review stream itself: opens one EventSource (when `enabled`). Each event refetches the review data it touches;
 * nothing here polls. Every reopen after the first open (the browser's own reconnect after a
 * server-side close or a network blip, or ours after a refresh) refetches the review queries once
 * to catch up on anything missed. When the stream is closed for good (the access token expired
 * and the browser's reconnect got a 401), the session is refreshed and the stream reopened.
 *
 * `unreadCount` counts the events received since mount or the last `resetUnread()` (call it on
 * the queue or My submissions page).
 *
 * Pages should call {@link useReviewStream} instead, which shares the layout's single connection.
 */
export function useReviewStreamConnection(onEvent?: (e: ReviewStreamEvent) => void, enabled = true) {
  const queryClient = useQueryClient();
  const [unreadCount, setUnreadCount] = useState(0);
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!enabled) return;
    let source: EventSource | null = null;
    let disposed = false;
    let reconnectsWithoutOpen = 0;
    let openedBefore = false;

    const refetchAll = () => {
      queryClient.invalidateQueries({ queryKey: REVIEW_KEY });
      queryClient.invalidateQueries({ queryKey: REVIEW_INBOX_KEY });
      queryClient.invalidateQueries({ queryKey: REVIEW_STATUSES_KEY });
    };

    const onReview = (message: MessageEvent) => {
      let event: ReviewStreamEvent;
      try {
        event = JSON.parse(message.data) as ReviewStreamEvent;
      } catch {
        return;
      }
      queryClient.invalidateQueries({ queryKey: reviewSubmissionKey(event.submissionId) });
      queryClient.invalidateQueries({ queryKey: REVIEW_INBOX_KEY });
      queryClient.invalidateQueries({ queryKey: REVIEW_STATUSES_KEY });
      queryClient.invalidateQueries({ queryKey: CASE_STATUSES_KEY });
      queryClient.invalidateQueries({ queryKey: MY_SUBMISSIONS_KEY });
      queryClient.invalidateQueries({ queryKey: APPROVED_COUNT_KEY });
      setUnreadCount((n) => n + 1);
      onEventRef.current?.(event);
    };

    const connect = () => {
      const es = new EventSource(REVIEW_STREAM_URL, { withCredentials: true });
      source = es;
      es.addEventListener("review", onReview as EventListener);
      es.onopen = () => {
        reconnectsWithoutOpen = 0;
        // The first open needs no catch-up (the page just fetched); every later one does.
        if (openedBefore) refetchAll();
        openedBefore = true;
      };
      es.onerror = () => {
        // CONNECTING: a network blip the browser retries by itself. CLOSED: it gave up (e.g. 401).
        if (es.readyState !== EventSource.CLOSED || disposed) return;
        es.close();
        if (reconnectsWithoutOpen >= MAX_RECONNECTS_WITHOUT_OPEN) return;
        reconnectsWithoutOpen += 1;
        refreshSession()
          .then(() => {
            if (!disposed) connect();
          })
          .catch(() => {
            // The refresh token was rejected: refreshSession already announced the expired session.
          });
      };
    };

    connect();
    return () => {
      disposed = true;
      source?.close();
    };
  }, [queryClient, enabled]);

  const resetUnread = useCallback(() => setUnreadCount(0), []);

  return { unreadCount, resetUnread };
}

/**
 * Live review updates for a page or the nav. Inside {@link ReviewStreamProvider} (the healer app's
 * layout) it shares that one connection, so the browser never holds more than one EventSource;
 * without a provider (a bare page in a unit test) it opens its own.
 */
export function useReviewStream(onEvent?: (e: ReviewStreamEvent) => void) {
  const shared = useContext(ReviewStreamContext);
  const own = useReviewStreamConnection(onEvent, shared === null);
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const subscribe = shared?.subscribe;
  useEffect(() => {
    if (!subscribe) return;
    return subscribe((e) => onEventRef.current?.(e));
  }, [subscribe]);

  return shared ? { unreadCount: shared.unreadCount, resetUnread: shared.resetUnread } : own;
}
