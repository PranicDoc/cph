import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { EventStreamProvider } from "./EventStreamProvider";

vi.mock("../lib/axios", () => ({
  default: { defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));

const toastSuccess = vi.fn();
vi.mock("sonner", () => ({ toast: { success: (...args: unknown[]) => toastSuccess(...args) } }));

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

function renderProvider() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/"]}>
        <EventStreamProvider enabled>
          <Routes>
            <Route path="/" element={<p>home</p>} />
            <Route path="/pdf-list" element={<p>pdf list page</p>} />
          </Routes>
        </EventStreamProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("EventStreamProvider", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    toastSuccess.mockReset();
    vi.stubGlobal("EventSource", FakeEventSource);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("pdfReadyEvent_showsOneToastPerJob", () => {
    renderProvider();
    const source = FakeEventSource.instances[0];

    act(() => source.emit("pdf-ready", { caseId: "case-1", jobId: "job-1" }));
    act(() => source.emit("pdf-ready", { caseId: "case-1", jobId: "job-1" }));
    expect(toastSuccess).toHaveBeenCalledTimes(1);
    expect(toastSuccess.mock.calls[0][0]).toBe("PDF ready");

    act(() => source.emit("pdf-ready", { caseId: "case-2", jobId: "job-2" }));
    expect(toastSuccess).toHaveBeenCalledTimes(2);
  });

  it("the toast action opens the PDFs page", () => {
    renderProvider();
    act(() => FakeEventSource.instances[0].emit("pdf-ready", { caseId: "case-1", jobId: "job-1" }));
    const options = toastSuccess.mock.calls[0][1] as { action: { label: string; onClick: () => void } };
    expect(screen.getByText("home")).toBeInTheDocument();

    act(() => options.action.onClick());

    expect(screen.getByText("pdf list page")).toBeInTheDocument();
  });
});
