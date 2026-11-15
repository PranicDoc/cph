import { useCallback, useMemo, useRef, type ReactNode } from "react";
import { Outlet, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { EventStreamContext, type EventStreamValue } from "../hooks/eventStreamContext";
import { useMyPermissions } from "../hooks/useMyPermissions";
import { useEventStreamConnection, type PdfReadyStreamEvent, type ReviewStreamEvent } from "../hooks/useEventStream";
import { hasPermission } from "../lib/adminAccess";

/**
 * Opens the one live event stream for the signed-in healer and shares it: the nav dots, the review
 * pages and the PDF list read it through {@link useEventStream}, so the browser holds a single
 * EventSource. A `pdf-ready` event also raises one "PDF ready" toast per job, with a shortcut to the
 * PDFs page. With `enabled` false nothing is opened.
 */
export function EventStreamProvider({ enabled, children }: { enabled: boolean; children: ReactNode }) {
  const navigate = useNavigate();
  const listeners = useRef(new Set<(e: ReviewStreamEvent) => void>());
  const pdfListeners = useRef(new Set<(e: PdfReadyStreamEvent) => void>());
  const toastedJobs = useRef(new Set<string>());

  const onPdfReady = useCallback(
    (event: PdfReadyStreamEvent) => {
      pdfListeners.current.forEach((l) => l(event));
      if (toastedJobs.current.has(event.jobId)) return;
      toastedJobs.current.add(event.jobId);
      toast.success("PDF ready", {
        id: `pdf-ready-${event.jobId}`,
        action: { label: "View PDFs", onClick: () => navigate("/pdf-list") },
      });
    },
    [navigate]
  );

  const { unreadCount, resetUnread } = useEventStreamConnection(
    (event) => listeners.current.forEach((l) => l(event)),
    enabled,
    onPdfReady
  );
  const subscribe = useCallback((listener: (e: ReviewStreamEvent) => void) => {
    listeners.current.add(listener);
    return () => {
      listeners.current.delete(listener);
    };
  }, []);
  const subscribePdfReady = useCallback((listener: (e: PdfReadyStreamEvent) => void) => {
    pdfListeners.current.add(listener);
    return () => {
      pdfListeners.current.delete(listener);
    };
  }, []);
  const value = useMemo<EventStreamValue>(
    () => ({ unreadCount, resetUnread, subscribe, subscribePdfReady }),
    [unreadCount, resetUnread, subscribe, subscribePdfReady]
  );
  return <EventStreamContext.Provider value={value}>{children}</EventStreamContext.Provider>;
}

/**
 * Route element for the healer app: the stream is open for users who can submit or review cases or
 * generate PDFs (the gate for "PDF ready" events is PDF:CREATE, which can exist without any CASE grant).
 */
export function EventStreamRoute() {
  const { data: permissions } = useMyPermissions();
  const enabled =
    hasPermission(permissions, "CASE", "UPDATE") ||
    hasPermission(permissions, "CASE", "REVIEW") ||
    hasPermission(permissions, "PDF", "CREATE");
  return (
    <EventStreamProvider enabled={enabled}>
      <Outlet />
    </EventStreamProvider>
  );
}
