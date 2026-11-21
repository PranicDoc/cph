import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import { EventStreamProvider } from "../context/EventStreamProvider";
import { PdfList } from "./PdfList";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
vi.mock("../context/PatientContext", () => ({
  usePatient: () => ({
    patients: [
      { healingCaseId: "c-old", fullName: "Older Result" },
      { healingCaseId: "c-new", fullName: "Newer Result" },
    ],
  }),
}));

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  readyState = 0;
  onerror: (() => void) | null = null;
  onopen: (() => void) | null = null;
  private listeners = new Map<string, ((e: MessageEvent) => void)[]>();
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  addEventListener(type: string, fn: (e: MessageEvent) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
  }
  close() {}
  emit(type: string, data: unknown) {
    const event = new MessageEvent(type, { data: JSON.stringify(data) });
    (this.listeners.get(type) ?? []).forEach((fn) => fn(event));
  }
}

class FakeIntersectionObserver {
  static instances: FakeIntersectionObserver[] = [];
  constructor(public callback: (entries: { isIntersecting: boolean }[]) => void) {
    FakeIntersectionObserver.instances.push(this);
  }
  observe() {}
  disconnect() {}
  unobserve() {}
}

/** One page of results; `positionAt` is the cursor for the next page (null: no more pages). */
const page = (caseId: string, positionAt: string | null = "cursor-1") => ({
  data: {
    data: {
      content: [{ healingCaseId: caseId, fileUrl: `f-${caseId}`, completedAt: "2026-11-15T10:00:00Z" }],
      positionAt,
    },
  },
});

function deferred<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: Error) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}
type Page = ReturnType<typeof page>;

const queryClient = new QueryClient();
const tree = (child: ReactNode = <PdfList />) => (
  <QueryClientProvider client={queryClient}>
    <MemoryRouter>
      <EventStreamProvider enabled>{child}</EventStreamProvider>
    </MemoryRouter>
  </QueryClientProvider>
);
const renderList = () => render(tree());

const emitPdfReady = (jobId = "j") =>
  act(() => FakeEventSource.instances[0].emit("pdf-ready", { caseId: "x", jobId }));
/** What scrolling to the end of the list does: fires the (possibly stale) observer's callback. */
const scrollToEnd = (observerIndex = 0) =>
  act(() => FakeIntersectionObserver.instances[observerIndex].callback([{ isIntersecting: true }]));

const pdfCalls = () => vi.mocked(api.get).mock.calls.filter((c) => String(c[0]).startsWith("/api/v1/cases/pdfs"));

describe("PdfList live refresh", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    FakeIntersectionObserver.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.stubGlobal("IntersectionObserver", FakeIntersectionObserver);
    vi.mocked(api.get).mockReset();
    vi.spyOn(console, "log").mockImplementation(() => {});
    vi.spyOn(console, "error").mockImplementation(() => {});
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("pdfReady_refetchesTheFirstPage", async () => {
    vi.mocked(api.get).mockResolvedValue(page("c-old"));
    renderList();
    await screen.findByText("Older Result");
    expect(pdfCalls()).toHaveLength(1);

    vi.mocked(api.get).mockResolvedValue(page("c-new"));
    emitPdfReady();

    await screen.findByText("Newer Result");
    expect(pdfCalls()).toHaveLength(2);
    expect(String(pdfCalls()[1][0])).toBe("/api/v1/cases/pdfs?limit=10");
  });

  it("pdfReady_usesTheCurrentSearchTerm", async () => {
    // No further pages: typing filters locally and does not fetch, so only the event can fetch.
    vi.mocked(api.get).mockResolvedValue(page("c-old", null));
    renderList();
    await screen.findByText("Older Result");

    fireEvent.change(screen.getByPlaceholderText("Search patients..."), { target: { value: "ann" } });
    vi.mocked(api.get).mockResolvedValue(page("c-new", null));
    emitPdfReady();

    await waitFor(() => expect(pdfCalls()).toHaveLength(2));
    expect(String(pdfCalls()[1][0])).toBe("/api/v1/cases/pdfs?limit=10&search=ann");
  });

  it("afterThePageUnmounts_aPdfReadyEventFetchesNothing", async () => {
    vi.mocked(api.get).mockResolvedValue(page("c-old"));
    const view = renderList();
    await screen.findByText("Older Result");

    // Only the page goes; the provider and its EventSource stay, so the event still arrives.
    view.rerender(tree(null));
    emitPdfReady();

    expect(pdfCalls()).toHaveLength(1);
  });

  it("olderResponseArrivingLast_doesNotOverwriteTheNewerList", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page("c-old"));
    renderList();
    await screen.findByText("Older Result");

    const slow = deferred<Page>();
    vi.mocked(api.get).mockReturnValueOnce(slow.promise as never).mockResolvedValueOnce(page("c-new"));
    emitPdfReady("j1");
    emitPdfReady("j2");
    await screen.findByText("Newer Result");

    await act(async () => slow.resolve(page("c-old")));

    await waitFor(() => expect(screen.getByText("Newer Result")).toBeInTheDocument());
    expect(screen.queryByText("Older Result")).not.toBeInTheDocument();
  });

  it("loadMoreTriggeredWhileAResetIsInFlight_isBlockedAndTheResetResultStands", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page("c-old"));
    renderList();
    await screen.findByText("Older Result");

    const reset = deferred<Page>();
    vi.mocked(api.get).mockReturnValueOnce(reset.promise as never);
    emitPdfReady();
    await waitFor(() => expect(pdfCalls()).toHaveLength(2));

    // The observer from before the reset is still alive and fires.
    scrollToEnd();
    expect(pdfCalls()).toHaveLength(2);

    await act(async () => reset.resolve(page("c-new")));

    await screen.findByText("Newer Result");
    expect(screen.queryByText("Older Result")).not.toBeInTheDocument();
    expect(screen.queryByText("Loading more...")).not.toBeInTheDocument();
  });

  it("aResetStartedAfterAnInFlightLoadMore_winsOverTheLateAppend", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page("c-old"));
    renderList();
    await screen.findByText("Older Result");

    const append = deferred<Page>();
    vi.mocked(api.get).mockReturnValueOnce(append.promise as never).mockResolvedValueOnce(page("c-new"));
    scrollToEnd();
    await screen.findByText("Loading more...");
    emitPdfReady();
    await screen.findByText("Newer Result");

    await act(async () => append.resolve(page("c-old")));

    expect(screen.queryByText("Older Result")).not.toBeInTheDocument();
    expect(screen.queryByText("Loading more...")).not.toBeInTheDocument();
  });

  it("whenTheLatestResetFails_theSpinnerIsCleared", async () => {
    vi.mocked(api.get).mockRejectedValueOnce(new Error("boom"));
    renderList();

    expect(await screen.findByText("No PDFs generated yet.")).toBeInTheDocument();
  });

  it("aSupersededResetThatFails_doesNotClearTheNewerRequestsLoadingState", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page("c-old"));
    renderList();
    await screen.findByText("Older Result");

    const first = deferred<Page>();
    const second = deferred<Page>();
    vi.mocked(api.get).mockReturnValueOnce(first.promise as never).mockReturnValueOnce(second.promise as never);
    emitPdfReady("j1");
    emitPdfReady("j2");
    await waitFor(() => expect(pdfCalls()).toHaveLength(3));

    // The older request fails while the newer one is still loading: no empty state may appear.
    await act(async () => first.reject(new Error("late failure")));
    expect(screen.queryByText("No PDFs generated yet.")).not.toBeInTheDocument();
    expect(screen.queryByText("Older Result")).not.toBeInTheDocument();

    await act(async () => second.resolve(page("c-new")));
    await screen.findByText("Newer Result");
  });

  it("whenTheLatestLoadMoreFails_theLoadingMoreIndicatorIsCleared", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page("c-old"));
    renderList();
    await screen.findByText("Older Result");

    const append = deferred<Page>();
    vi.mocked(api.get).mockReturnValueOnce(append.promise as never);
    scrollToEnd();
    await screen.findByText("Loading more...");

    await act(async () => append.reject(new Error("boom")));

    expect(screen.queryByText("Loading more...")).not.toBeInTheDocument();
    expect(screen.getByText("Older Result")).toBeInTheDocument();
  });

  it("aSupersededLoadMoreThatFails_doesNotClearTheNewerOnesIndicator", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page("c-old"));
    renderList();
    await screen.findByText("Older Result");

    const first = deferred<Page>();
    const second = deferred<Page>();
    vi.mocked(api.get).mockReturnValueOnce(first.promise as never).mockReturnValueOnce(second.promise as never);
    scrollToEnd();
    await screen.findByText("Loading more...");
    scrollToEnd();
    await waitFor(() => expect(pdfCalls()).toHaveLength(3));

    await act(async () => first.reject(new Error("late failure")));
    expect(screen.getByText("Loading more...")).toBeInTheDocument();

    await act(async () => second.resolve(page("c-new", null)));
    await waitFor(() => expect(screen.queryByText("Loading more...")).not.toBeInTheDocument());
    expect(screen.getByText("Newer Result")).toBeInTheDocument();
  });

  it("aUserSearchGoesThroughTheGuard_soALaterRefreshWinsOverItsSlowResponse", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page("c-old"));
    renderList();
    await screen.findByText("Older Result");

    const slowSearch = deferred<Page>();
    vi.mocked(api.get).mockReturnValueOnce(slowSearch.promise as never).mockResolvedValueOnce(page("c-new"));
    fireEvent.change(screen.getByPlaceholderText("Search patients..."), { target: { value: "ann" } });
    await waitFor(() => expect(pdfCalls()).toHaveLength(2)); // after the 500 ms debounce
    expect(String(pdfCalls()[1][0])).toBe("/api/v1/cases/pdfs?limit=10&search=ann");
    emitPdfReady();
    await screen.findByText("Newer Result");

    await act(async () => slowSearch.resolve(page("c-old")));

    expect(screen.queryByText("Older Result")).not.toBeInTheDocument();
  });
});
