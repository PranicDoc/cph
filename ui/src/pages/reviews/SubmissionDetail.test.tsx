import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import SubmissionDetail from "./SubmissionDetail";
import api from "../../lib/axios";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));
vi.mock("./PdfDocumentView", () => ({
  default: ({ fileUrl }: { fileUrl: string }) => <div data-testid="pdf" data-url={fileUrl} />,
}));

class FakeEventSource {
  static CLOSED = 2;
  readyState = 1;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  addEventListener() {}
  close() {}
}

const comment = (over: Record<string, unknown>) => ({
  id: "c1", submissionId: "s1", revisionNo: 2, parentId: null, authorId: "r1", authorName: "Asha",
  authorSide: "REVIEWER", page: 1, x: 0.4, y: 0.6, body: "Fix the spacing", createdAt: "2026-11-02T10:00:00Z",
  resolvedAt: null, ...over,
});

function renderDetail() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/submissions/s1"]}>
        <Routes>
          <Route path="/submissions/:submissionId" element={<SubmissionDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("SubmissionDetail", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url.endsWith("/submissions/s1"))
        return Promise.resolve({
          data: {
            id: "s1", caseId: "c1", healerId: "me", reviewerId: "r1", stage: "REVIEWER", status: "CHANGES_REQUESTED",
            dismissedFromApproved: false, dismissReason: null, submittedVia: "MANUAL", latestRevisionNo: 2,
            createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-02T10:00:00Z",
            revisions: [1, 2].map((n) => ({ revisionNo: n, pdfJobId: null, submittedVia: "MANUAL", submittedAt: "2026-11-01T10:00:00Z" })),
          },
        });
      if (url.endsWith("/submissions/s1/comments"))
        return Promise.resolve({
          data: [comment({}), comment({ id: "c0", revisionNo: 1, body: "Older note", createdAt: "2026-11-01T11:00:00Z" })],
        });
      const m = url.match(/revisions\/(\d+)\/file$/);
      if (m) return Promise.resolve({ data: { url: `https://r2/rev${m[1]}.pdf` } });
      return Promise.reject(new Error("unexpected " + url));
    });
    vi.mocked(api.post).mockResolvedValue({ data: comment({ id: "c2", parentId: "c1", authorSide: "HEALER", body: "Done" }) });
  });

  const BANNER = "Your instructor asked for changes.";
  const serve = (status: string) =>
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url.endsWith("/submissions/s1"))
        return Promise.resolve({
          data: {
            id: "s1", caseId: "c1", healerId: "me", reviewerId: null, stage: "REVIEWER", status,
            dismissedFromApproved: false, dismissReason: null, submittedVia: "MANUAL", latestRevisionNo: 1,
            createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-02T10:00:00Z",
            revisions: [{ revisionNo: 1, pdfJobId: null, submittedVia: "MANUAL", submittedAt: "2026-11-01T10:00:00Z" }],
          },
        });
      if (url.endsWith("/comments")) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: { url: "https://r2/rev1.pdf" } });
    });

  it("shows the changes-requested banner, edit link and resubmit, which submits the case", async () => {
    const user = userEvent.setup();
    serve("CHANGES_REQUESTED");
    renderDetail();

    expect(await screen.findByText(BANNER)).toBeInTheDocument();
    expect(screen.getByText(/nothing changed since R1/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit case" })).toHaveAttribute("href", "/session/c1");

    await user.click(screen.getByRole("button", { name: "Resubmit for review" }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/cases/c1/submit"));
  });

  it("a dismissed submission can be submitted again, without the banner", async () => {
    serve("DISMISSED");
    renderDetail();
    expect(await screen.findByRole("button", { name: "Submit for review" })).toBeInTheDocument();
    expect(screen.queryByText(BANNER)).not.toBeInTheDocument();
  });

  it.each(["PENDING_REVIEW", "APPROVED"])("shows no banner or resubmit bar when %s", async (status) => {
    serve(status);
    renderDetail();
    await screen.findByTestId("pdf");
    expect(screen.queryByText(BANNER)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /submit for review/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Edit case" })).not.toBeInTheDocument();
  });

  it("healerThread_hasReplyButNoApprove", async () => {
    const user = userEvent.setup();
    renderDetail();

    expect(await screen.findByText("Fix the spacing")).toBeInTheDocument();
    // An older-version thread is labelled.
    expect(screen.getByText("on version 1")).toBeInTheDocument();

    // Read-only: no pin placement, no resolve, no approve / request changes.
    expect(screen.queryByRole("button", { name: /add pin/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /resolve/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /approve/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /request changes/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/general comment/i)).not.toBeInTheDocument();

    // Reply is available and posts to the healer route with the thread as parent.
    const replyBoxes = screen.getAllByRole("textbox", { name: "Reply" });
    await user.type(replyBoxes[0], "Done");
    await user.click(screen.getAllByRole("button", { name: "Reply" })[0]);

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/submissions/s1/comments", {
        parentId: expect.stringMatching(/^c[01]$/),
        body: "Done",
      })
    );
  });
});
