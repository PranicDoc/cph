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
  createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-01T10:00:00Z", revisions: [],
  caseTitle: "Chronic migraine", healerName: "Meera", reviewerName: "Asha Rao", ...over,
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
    expect(within(rows[0]).getByRole("link", { name: "Open and resubmit" })).toHaveAttribute("href", "/submissions/s2");
    expect(within(rows[0]).getByRole("link", { name: /view case/i })).toHaveAttribute("href", "/cases/c2/summary");
    expect(within(rows[1]).getByRole("link", { name: "View" })).toHaveAttribute("href", "/submissions/s1");
    expect(within(rows[1]).getByText("Approved")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/api/v1/reviews/my-submissions");
  });

  it("titles each row with the case and names the instructor when there is one", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        submission({ id: "s1", caseId: "c1" }),
        submission({ id: "s2", caseId: "c2", caseTitle: "Back pain", reviewerId: null, reviewerName: null }),
        submission({ id: "s3", caseId: "c3", caseTitle: "Insomnia", reviewerId: "gone", reviewerName: null }),
      ],
    });
    renderPage();
    const rows = await screen.findAllByTestId("submission-row", {}, { timeout: 5000 });

    expect(within(rows[0]).getByText("Chronic migraine")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Instructor: Asha Rao")).toBeInTheDocument();
    expect(within(rows[1]).getByText("Back pain")).toBeInTheDocument();
    expect(within(rows[1]).queryByText(/Instructor:/)).not.toBeInTheDocument();
    expect(within(rows[2]).getByText("Instructor: Unknown")).toBeInTheDocument();
  });

  it("counts approved, waiting and needs-attention cases from the list", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        submission({ id: "a", caseId: "c1", status: "PENDING_REVIEW" }),
        submission({ id: "b", caseId: "c2", status: "CHANGES_REQUESTED" }),
        submission({ id: "c", caseId: "c3", status: "DISMISSED" }),
        submission({ id: "d", caseId: "c4", status: "APPROVED" }),
        submission({ id: "e", caseId: "c5", status: "APPROVED" }),
        submission({ id: "f", caseId: "c6", status: "APPROVED" }),
        // An older submission of a case already counted: only the newest one is the case's state.
        submission({ id: "g", caseId: "c1", status: "DISMISSED" }),
      ],
    });
    renderPage();

    const card = async (name: string) => within(await screen.findByRole("group", { name }));
    expect((await card("Approved cases")).getByText("3")).toBeInTheDocument();
    expect((await card("Waiting for your instructor")).getByText("1")).toBeInTheDocument();
    expect((await card("Needs your attention")).getByText("2")).toBeInTheDocument();
  });

  it.each([
    ["PENDING_REVIEW", "Open"],
    ["CHANGES_REQUESTED", "Open and resubmit"],
    ["DISMISSED", "Open and resubmit"],
    ["APPROVED", "View"],
  ])("labels the row action of a %s submission '%s'", async (status, label) => {
    vi.mocked(api.get).mockResolvedValue({ data: [submission({ status })] });
    renderPage();
    expect(await screen.findByRole("link", { name: label })).toHaveAttribute("href", "/submissions/s1");
  });

  it("a superseded submission only offers 'View', as a secondary action", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        submission({ id: "new", caseId: "c1", status: "CHANGES_REQUESTED" }),
        submission({ id: "old", caseId: "c1", status: "DISMISSED" }),
      ],
    });
    renderPage();

    const rows = await screen.findAllByTestId("submission-row");
    const current = within(rows[0]).getByRole("link", { name: "Open and resubmit" });
    const superseded = within(rows[1]).getByRole("link", { name: "View" });
    expect(superseded).toHaveAttribute("href", "/submissions/old");
    expect(within(rows[1]).queryByRole("link", { name: "Open and resubmit" })).not.toBeInTheDocument();
    expect(current).toHaveAttribute("data-variant", "default");
    expect(superseded).toHaveAttribute("data-variant", "outline");
  });

  it("says so when nothing was submitted yet", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] });
    renderPage();
    expect(await screen.findByText(/haven't submitted any cases/i)).toBeInTheDocument();
  });
});
