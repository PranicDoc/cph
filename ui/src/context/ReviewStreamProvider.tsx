import { useCallback, useMemo, useRef, type ReactNode } from "react";
import { Outlet } from "react-router-dom";
import { ReviewStreamContext, type ReviewStreamValue } from "../hooks/reviewStreamContext";
import { useMyPermissions } from "../hooks/useMyPermissions";
import { useReviewStreamConnection, type ReviewStreamEvent } from "../hooks/useReviewStream";
import { hasPermission } from "../lib/adminAccess";

/**
 * Opens the one live review stream for the signed-in healer and shares it: the nav dots and every
 * review page read it through {@link useReviewStream}, so the browser holds a single EventSource.
 * With `enabled` false (no case permissions) nothing is opened.
 */
export function ReviewStreamProvider({ enabled, children }: { enabled: boolean; children: ReactNode }) {
  const listeners = useRef(new Set<(e: ReviewStreamEvent) => void>());
  const { unreadCount, resetUnread } = useReviewStreamConnection(
    (event) => listeners.current.forEach((l) => l(event)),
    enabled
  );
  const subscribe = useCallback((listener: (e: ReviewStreamEvent) => void) => {
    listeners.current.add(listener);
    return () => {
      listeners.current.delete(listener);
    };
  }, []);
  const value = useMemo<ReviewStreamValue>(
    () => ({ unreadCount, resetUnread, subscribe }),
    [unreadCount, resetUnread, subscribe]
  );
  return <ReviewStreamContext.Provider value={value}>{children}</ReviewStreamContext.Provider>;
}

/** Route element for the healer app: the stream is open only for users who can submit or review cases. */
export function ReviewStreamRoute() {
  const { data: permissions } = useMyPermissions();
  const enabled = hasPermission(permissions, "CASE", "UPDATE") || hasPermission(permissions, "CASE", "REVIEW");
  return (
    <ReviewStreamProvider enabled={enabled}>
      <Outlet />
    </ReviewStreamProvider>
  );
}
