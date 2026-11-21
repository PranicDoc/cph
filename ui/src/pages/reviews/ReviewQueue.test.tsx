import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import ReviewQueue from "./ReviewQueue";
import api from "../../lib/axios";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));

class FakeEventSource {
  static CLOSED = 2;
  readyState = 1;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  addEventListener() {}
  close() {}
}

describe("ReviewQueue", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("EventSource", FakeEventSource);
  });

  it("queueListsOldestFirst in the order the server returns, each row linking to its viewer", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        { submissionId: "s-old", healerName: "Bala", caseTitle: "Asthma", latestRevisionNo: 1, status: "PENDING_REVIEW", submittedAt: "2026-11-01T10:00:00Z" },
        { submissionId: "s-new", healerName: "Asha", caseTitle: "Migraine", latestRevisionNo: 2, status: "CHANGES_REQUESTED", submittedAt: "2026-11-05T10:00:00Z" },
      ],
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ReviewQueue />
        </MemoryRouter>
      </QueryClientProvider>
    );

    const rows = await screen.findAllByRole("link");
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText("Bala")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Asthma")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Asha")).toBeInTheDocument();
    expect(rows[0]).toHaveAttribute("href", "/reviews/s-old");
    expect(api.get).toHaveBeenCalledWith("/api/v1/reviews/inbox", { params: undefined });
  });

  it("shows an empty state when nothing is assigned", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ReviewQueue />
        </MemoryRouter>
      </QueryClientProvider>
    );
    expect(await screen.findByText(/no submissions/i)).toBeInTheDocument();
  });
});
