import { useCallback, useEffect, useRef, useState } from "react";

import api from "../../lib/axios";

// This project uses cookie auth (`withCredentials: true`, no `Authorization`
// header) — see cph/ui/src/lib/axios.ts. That is why `navigator.sendBeacon`,
// which cannot set custom headers, still carries the practitioner's session
// automatically on a same-origin call below: the browser attaches the
// session cookie to the beacon request the same way it does to a normal
// fetch/XHR. This would NOT transfer unmodified to a codebase using
// localStorage-JWT + an `Authorization: Bearer` header — flagging the
// coupling explicitly so a future auth migration doesn't silently break it.

/**
 * READ-02: server-authoritative open/close/duration tracking for a single
 * mandatory-document reading session.
 */
export interface ReadingSessionResponse {
  id: string;
  subjectId: string;
  consentType: string;
  policyVersion: number;
  openedAt: string;
  closedAt: string | null;
  durationSeconds: number | null;
  scrollCompleted: boolean;
}

export interface ReadingSessionLifecycleArgs {
  consentType: string;
  policyVersion: number;
  hasReachedEndRef: React.MutableRefObject<boolean>;
}

export function useReadingSessionLifecycle(
  args: ReadingSessionLifecycleArgs
): { sessionId: string | null } {
  const { consentType, policyVersion, hasReachedEndRef } = args;

  const [sessionId, setSessionId] = useState<string | null>(null);
  const sessionIdRef = useRef<string | null>(null);
  const openedRef = useRef(false);
  const closedRef = useRef(false);

  // Open exactly once per mount. openedRef is set BEFORE the request is
  // issued so that React 18/19 StrictMode's deliberate dev-only
  // mount->unmount->remount cycle (refs survive it, state does not) cannot
  // create two reading_session rows for a single practitioner visit.
  useEffect(() => {
    if (openedRef.current) return;
    openedRef.current = true;

    api.post("/api/v1/consents/reading-sessions", { consentType, policyVersion })
      .then(({ data }: { data: ReadingSessionResponse }) => {
        sessionIdRef.current = data.id;
        setSessionId(data.id);
      })
      .catch((error) => {
        // Tracking is non-blocking per D-02 — a failed open call must never
        // surface any user-facing notification to the practitioner.
        console.warn("useReadingSessionLifecycle: failed to open reading session", error);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [consentType, policyVersion]);

  const closeSession = useCallback((useBeacon: boolean) => {
    if (closedRef.current || sessionIdRef.current === null) return;
    // Set BEFORE dispatch so the unmount path and the visibilitychange path
    // can never both fire a close for the same session (T-33-11).
    closedRef.current = true;

    const payload = JSON.stringify({ scrollCompleted: hasReachedEndRef.current });

    if (
      useBeacon &&
      typeof navigator !== "undefined" &&
      typeof navigator.sendBeacon === "function"
    ) {
      navigator.sendBeacon(
        `/cph/api/v1/consents/reading-sessions/${sessionIdRef.current}/close`,
        new Blob([payload], { type: "application/json" })
      );
      return;
    }

    api
      .patch(`/api/v1/consents/reading-sessions/${sessionIdRef.current}/close`, {
        scrollCompleted: hasReachedEndRef.current,
      })
      .catch((error) => {
        console.warn("useReadingSessionLifecycle: failed to close reading session", error);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Beacon-on-hidden: covers tab close / browser close, where an in-SPA
  // unmount effect never runs. The visibilitychange event is used here
  // rather than the two page-teardown events other codebases sometimes
  // reach for, since mobile Safari largely ignores one of them and the
  // other is desktop-only — visibilitychange is the reliable cross-platform
  // signal.
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "hidden") {
        closeSession(true);
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [closeSession]);

  // Axios-on-unmount: covers in-SPA navigation and tab-switching (D-01's
  // conditional-mount tab bar), neither of which fires visibilitychange.
  useEffect(() => {
    return () => {
      closeSession(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { sessionId };
}
