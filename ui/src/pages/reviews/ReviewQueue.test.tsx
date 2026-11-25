import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

    const links = await screen.findAllByRole("link");
    expect(links).toHaveLength(2);
    const rows = screen.getAllByRole("row").slice(1); // after the header row
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText("Bala")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Asthma")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Asha")).toBeInTheDocument();
    expect(links[0]).toHaveAttribute("href", "/reviews/s-old");
    expect(links[0]).toHaveTextContent("Open review");
    expect(links[1]).toHaveAttribute("href", "/reviews/s-new");
    expect(api.get).toHaveBeenCalledWith("/api/v1/reviews/inbox", { params: undefined });
  });

  it("segmented filter shows a count per status and filters through the API", async () => {
    const user = userEvent.setup();
    const item = (id: string, status: string) => ({
      submissionId: id, healerName: "Bala", caseTitle: id, latestRevisionNo: 1, status, submittedAt: "2026-11-01T10:00:00Z",
    });
    const all = [item("a", "PENDING_REVIEW"), item("b", "PENDING_REVIEW"), item("c", "APPROVED")];
    vi.mocked(api.get).mockImplementation((_url: string, config?: { params?: { status?: string } }) =>
      Promise.resolve({ data: all.filter((i) => !config?.params?.status || i.status === config.params.status) })
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ReviewQueue />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(await screen.findByRole("button", { name: "All 3" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "In review 2" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Changes requested 0" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dismissed 0" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View" })).toHaveAttribute("href", "/reviews/c");

    await user.click(screen.getByRole("button", { name: "Approved 1" }));

    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith("/api/v1/reviews/inbox", { params: { status: "APPROVED" } })
    );
    await waitFor(() => expect(screen.getAllByRole("link")).toHaveLength(1));
    // Counts stay those of the whole queue while filtered.
    expect(screen.getByRole("button", { name: "All 3" })).toBeInTheDocument();
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
