import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";

import { useEventStream, EVENT_STREAM_URL } from "./useEventStream";
import { refreshSession } from "../lib/axios";

vi.mock("../lib/axios", () => ({
  default: { defaults: { baseURL: "/cph" } },
  refreshSession: vi.fn(),
}));

/** Minimal stand-in for the browser EventSource (jsdom has none). */
class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instances: FakeEventSource[] = [];

  readyState = FakeEventSource.CONNECTING;
  onerror: ((e: Event) => void) | null = null;
  onopen: ((e: Event) => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((e: MessageEvent) => void)[]>();

  constructor(
    public url: string,
    public init?: EventSourceInit
  ) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, fn: (e: MessageEvent) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
  }

  removeEventListener() {}

  close() {
    this.closed = true;
    this.readyState = FakeEventSource.CLOSED;
  }

  /* test drivers */
  open() {
    this.readyState = FakeEventSource.OPEN;
    this.onopen?.(new Event("open"));
  }

  emit(type: string, data: unknown) {
    const event = new MessageEvent(type, { data: JSON.stringify(data) });
    (this.listeners.get(type) ?? []).forEach((fn) => fn(event));
  }

  /** What the browser does when the stream answers 401: fail and stop retrying. */
  failClosed() {
    this.readyState = FakeEventSource.CLOSED;
    this.onerror?.(new Event("error"));
  }
}

function setup(onEvent = vi.fn()) {
  const queryClient = new QueryClient();
  const invalidate = vi.spyOn(queryClient, "invalidateQueries");
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  const hook = renderHook(() => useEventStream(onEvent), { wrapper });
  return { hook, invalidate, onEvent };
}

const keysOf = (spy: ReturnType<typeof vi.spyOn>) =>
  spy.mock.calls.map((c) => (c[0] as { queryKey: unknown[] }).queryKey);

describe("useEventStream", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.mocked(refreshSession).mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("opens the stream with credentials on the API base path", () => {
    setup();

    expect(EVENT_STREAM_URL).toBe("/cph/api/v1/events/stream");
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toBe("/cph/api/v1/events/stream");
    expect(FakeEventSource.instances[0].init).toEqual({ withCredentials: true });
  });

  it("invalidatesQueriesOnEvent", () => {
    const { hook, invalidate, onEvent } = setup();
    const source = FakeEventSource.instances[0];
    act(() => source.open());

    const event = { submissionId: "sub-1", type: "REVISION_ADDED", revisionNo: 2 };
    act(() => source.emit("review", event));

    expect(keysOf(invalidate)).toEqual(
      expect.arrayContaining([["review", "sub-1"], ["review-inbox"], ["review-statuses"], ["review", "case-statuses"]])
    );
    expect(onEvent).toHaveBeenCalledWith(event);
    expect(hook.result.current.unreadCount).toBe(1);

    act(() => source.emit("review", { ...event, revisionNo: 3 }));
    expect(hook.result.current.unreadCount).toBe(2);

    act(() => hook.result.current.resetUnread());
    expect(hook.result.current.unreadCount).toBe(0);
  });

  it("pdfReadyEvent_invalidatesCaseStatusesAndNotifiesSubscribers", () => {
    const onPdfReady = vi.fn();
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    // The PDF list is not a query: it refreshes itself from the pdf-ready callback.
    renderHook(() => useEventStream(undefined, onPdfReady), { wrapper });
    const source = FakeEventSource.instances[0];
    act(() => source.open());

    const event = { caseId: "case-1", jobId: "job-1" };
    act(() => source.emit("pdf-ready", event));

    expect(keysOf(invalidate)).toEqual([["review", "case-statuses"]]);
    expect(onPdfReady).toHaveBeenCalledWith(event);
  });

  it("a pdf-ready event does not count as an unread review event", () => {
    const { hook } = setup();
    const source = FakeEventSource.instances[0];
    act(() => source.open());

    act(() => source.emit("pdf-ready", { caseId: "case-1", jobId: "job-1" }));

    expect(hook.result.current.unreadCount).toBe(0);
  });

  it("reconnectsAfterRefreshOn401", async () => {
    vi.mocked(refreshSession).mockResolvedValue(undefined);
    const { invalidate } = setup();
    const first = FakeEventSource.instances[0];
    act(() => first.open());
    invalidate.mockClear();

    // The access token expired; the browser's own reconnect was answered 401.
    act(() => first.failClosed());

    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2));
    expect(refreshSession).toHaveBeenCalledTimes(1);
    expect(first.closed).toBe(true);
    const second = FakeEventSource.instances[1];
    expect(second.init).toEqual({ withCredentials: true });

    act(() => second.open());
    // Refetch once to catch up on anything missed while disconnected.
    expect(keysOf(invalidate)).toEqual([["review"], ["review-inbox"], ["review-statuses"]]);
  });

  it("refetchesOnceAfterOrdinaryReconnect", () => {
    const { invalidate } = setup();
    const source = FakeEventSource.instances[0];

    // First open: the page has just fetched, so nothing to catch up on.
    act(() => source.open());
    expect(invalidate).not.toHaveBeenCalled();

    // The server closed the stream (30 min timeout) or the network blipped; the browser
    // reconnects the same EventSource by itself.
    act(() => {
      source.readyState = FakeEventSource.CONNECTING;
      source.onerror?.(new Event("error"));
    });
    act(() => source.open());

    expect(keysOf(invalidate)).toEqual([["review"], ["review-inbox"], ["review-statuses"]]);
    expect(refreshSession).not.toHaveBeenCalled();
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("stops when the refresh itself is rejected (session expired)", async () => {
    vi.mocked(refreshSession).mockRejectedValue(new Error("401"));
    setup();
    act(() => FakeEventSource.instances[0].failClosed());

    await waitFor(() => expect(refreshSession).toHaveBeenCalledTimes(1));
    await new Promise((r) => setTimeout(r, 10));
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("ignores a transient error while the browser is still reconnecting", () => {
    setup();
    const source = FakeEventSource.instances[0];
    act(() => {
      source.readyState = FakeEventSource.CONNECTING;
      source.onerror?.(new Event("error"));
    });

    expect(refreshSession).not.toHaveBeenCalled();
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("closes the stream on unmount", () => {
    const { hook } = setup();
    hook.unmount();
    expect(FakeEventSource.instances[0].closed).toBe(true);
  });
});
