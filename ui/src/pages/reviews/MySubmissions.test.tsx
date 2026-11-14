import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import MySubmissions from "./MySubmissions";
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

const revision = (n: number, via: "MANUAL" | "AUTO", at: string) => ({
  revisionNo: n, pdfJobId: null, submittedVia: via, submittedAt: at,
});

const submission = (over: Record<string, unknown>) => ({
  id: "s1", caseId: "c1", healerId: "me", reviewerId: "r1", stage: "REVIEWER", status: "PENDING_REVIEW",
  dismissedFromApproved: false, dismissReason: null, submittedVia: "MANUAL", latestRevisionNo: 1,
  createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-01T10:00:00Z", revisions: [], ...over,
});

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MySubmissions />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("MySubmissions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("EventSource", FakeEventSource);
  });

  it("mySubmissions_listsHistoryWithRevisions", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        submission({
          id: "s2", caseId: "c2", status: "CHANGES_REQUESTED", latestRevisionNo: 2,
          revisions: [revision(1, "MANUAL", "2026-11-02T10:00:00Z"), revision(2, "AUTO", "2026-11-04T10:00:00Z")],
        }),
        submission({
          id: "s1", caseId: "c1", status: "APPROVED", latestRevisionNo: 1,
          revisions: [revision(1, "MANUAL", "2026-10-20T10:00:00Z")],
        }),
      ],
    });
    renderPage();

    const rows = await screen.findAllByTestId("submission-row", {}, { timeout: 5000 });
    expect(rows).toHaveLength(2);

    // Newest first, each with its status badge and every revision.
    expect(within(rows[0]).getByText("Changes requested")).toBeInTheDocument();
    expect(within(rows[0]).getByText(/Version 1/)).toBeInTheDocument();
    expect(within(rows[0]).getByText(/Version 2/)).toBeInTheDocument();
    expect(within(rows[0]).getByText(/automatic/i)).toBeInTheDocument();
    expect(within(rows[0]).getByRole("link", { name: /open submission/i })).toHaveAttribute("href", "/submissions/s2");
    expect(within(rows[1]).getByText("Approved")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/api/v1/reviews/my-submissions");
  });

  it("says so when nothing was submitted yet", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] });
    renderPage();
    expect(await screen.findByText(/haven't submitted any cases/i)).toBeInTheDocument();
  });
});
