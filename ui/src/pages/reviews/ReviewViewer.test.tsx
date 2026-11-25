import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import ReviewViewer from "./ReviewViewer";
import api from "../../lib/axios";

const auth = vi.hoisted(() => ({ user: { id: "me", role: "HEALER" } }));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));
// The PDF render needs a real canvas; the test only cares which file is handed to it.
vi.mock("./PdfDocumentView", () => ({
  default: ({ fileUrl }: { fileUrl: string }) => <div data-testid="pdf" data-url={fileUrl} />,
}));

class FakeEventSource {
  static CLOSED = 2;
  static instances: FakeEventSource[] = [];
  readyState = 1;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  listeners: Record<string, (m: MessageEvent) => void> = {};
  constructor() {
    FakeEventSource.instances.push(this);
  }
  addEventListener(type: string, fn: (m: MessageEvent) => void) {
    this.listeners[type] = fn;
  }
  close() {}
}

function submission(latestRevisionNo: number) {
  return {
    id: "s1", caseId: "c1", healerId: "h1", reviewerId: "me", stage: "REVIEWER", status: "PENDING_REVIEW",
    dismissedFromApproved: false, dismissReason: null, submittedVia: "MANUAL", latestRevisionNo,
    createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-01T10:00:00Z",
    caseTitle: "Migraine", healerName: "Meera", reviewerName: "Asha",
    revisions: Array.from({ length: latestRevisionNo }, (_, i) => ({
      revisionNo: i + 1, pdfJobId: null, submittedVia: "MANUAL", submittedAt: "2026-11-01T10:00:00Z",
    })),
  };
}

function renderViewer() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/reviews/s1"]}>
        <Routes>
          <Route path="/reviews/:submissionId" element={<ReviewViewer />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

let currentLatest = 1;
let currentComments: unknown[] = [];
function mockApi() {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url.endsWith("/inbox/s1/comments")) return Promise.resolve({ data: currentComments });
    if (url.endsWith("/inbox/s1")) return Promise.resolve({ data: submission(currentLatest) });
    const m = url.match(/revisions\/(\d+)\/file$/);
    if (m) return Promise.resolve({ data: { url: `https://r2/rev${m[1]}.pdf` } });
    return Promise.reject(new Error("unexpected " + url));
  });
}

describe("ReviewViewer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    FakeEventSource.instances = [];
    currentLatest = 1;
    currentComments = [];
    auth.user = { id: "me", role: "HEALER" };
    vi.stubGlobal("EventSource", FakeEventSource);
    mockApi();
  });

  it("titles the page from the submission and explains what approving does", async () => {
    renderViewer();
    expect(await screen.findByRole("heading", { name: "Migraine" })).toBeInTheDocument();
    expect(screen.getByText(/^Meera, submitted/)).toBeInTheDocument();
    expect(screen.getByText(/^Approving locks this revision/)).toBeInTheDocument();
    // The assigned reviewer is not acting as super admin.
    expect(screen.queryByText("You are acting as super admin.")).not.toBeInTheDocument();
  });

  const serveSubmission = (over: Record<string, unknown>) => {
    const base = vi.mocked(api.get).getMockImplementation()!;
    vi.mocked(api.get).mockImplementation((url: string) =>
      url.endsWith("/inbox/s1") ? Promise.resolve({ data: { ...submission(1), ...over } }) : base(url)
    );
  };

  it("opens a submission with no assigned reviewer for a super admin, who can decide and comment", async () => {
    const user = userEvent.setup();
    auth.user = { id: "sa", role: "SUPADM" };
    serveSubmission({ reviewerId: null, reviewerName: null });
    vi.mocked(api.post).mockResolvedValue({ data: {} });
    renderViewer();
    await screen.findByTestId("pdf");

    expect(screen.getByRole("heading", { name: "Migraine" })).toBeInTheDocument();
    expect(screen.getByText(/^Meera, submitted/)).toBeInTheDocument();
    expect(screen.getByText("You are acting as super admin.")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: "Request changes" })).toBeEnabled());

    await user.type(screen.getByRole("textbox", { name: "Add a general comment" }), "Looks fine");
    await user.click(screen.getByRole("button", { name: "Comment" }));
    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith(
        expect.stringContaining("/inbox/s1/comments"),
        expect.objectContaining({ body: "Looks fine", revisionNo: 1 })
      )
    );

    await user.click(screen.getByRole("button", { name: "Approve" }));
    await waitFor(() =>
      expect(vi.mocked(api.post).mock.calls.some((c) => String(c[0]).includes("/s1/approve"))).toBe(true)
    );
  });

  it("warns a super admin on a submission assigned to someone else", async () => {
    auth.user = { id: "sa", role: "SUPADM" };
    renderViewer();
    expect(await screen.findByText("You are acting as super admin.")).toBeInTheDocument();
  });

  it("does not warn a super admin who is the assigned reviewer", async () => {
    auth.user = { id: "me", role: "SUPADM" };
    renderViewer();
    await screen.findByRole("heading", { name: "Migraine" });
    expect(screen.queryByText("You are acting as super admin.")).not.toBeInTheDocument();
  });

  it("downloadButtonPresent once the file URL is known", async () => {
    renderViewer();
    const button = await screen.findByRole("button", { name: /download/i });
    await waitFor(() => expect(button).toBeEnabled());
    expect(await screen.findByTestId("pdf")).toHaveAttribute("data-url", "https://r2/rev1.pdf");
  });

  it("viewerShowsRefreshBannerOnNewerRevisionEvent, and clicking it loads the new revision", async () => {
    const user = userEvent.setup();
    renderViewer();
    await screen.findByTestId("pdf");
    expect(screen.queryByText("New version available — refresh")).not.toBeInTheDocument();

    currentLatest = 2;
    act(() => {
      FakeEventSource.instances[0].listeners["review"](
        new MessageEvent("review", {
          data: JSON.stringify({ submissionId: "s1", type: "REVISION_ADDED", revisionNo: 2 }),
        })
      );
    });

    const banner = await screen.findByText("New version available — refresh");
    // Still showing revision 1 until the reviewer opts in.
    expect(screen.getByTestId("pdf")).toHaveAttribute("data-url", "https://r2/rev1.pdf");

    await user.click(banner);

    await waitFor(() => expect(screen.getByTestId("pdf")).toHaveAttribute("data-url", "https://r2/rev2.pdf"));
    expect(screen.queryByText("New version available — refresh")).not.toBeInTheDocument();
  });

  it("reassigned403_showsMovedMessage instead of crashing", async () => {
    vi.mocked(api.get).mockRejectedValue({ isAxiosError: true, response: { status: 403, data: {} } });
    renderViewer();
    expect(await screen.findByText("This submission was moved to another instructor")).toBeInTheDocument();
    expect(screen.queryByTestId("pdf")).not.toBeInTheDocument();
  });

  it("lists a thread and resolves it, then refetches the comments", async () => {
    const user = userEvent.setup();
    currentComments = [
      { id: "c1", submissionId: "s1", revisionNo: 1, parentId: null, authorId: "me", authorName: "Me",
        authorSide: "REVIEWER", page: 1, x: 0.5, y: 0.5, body: "Check this", createdAt: "2026-11-02T10:00:00Z",
        resolvedAt: null },
    ];
    vi.mocked(api.post).mockResolvedValue({ data: {} });
    renderViewer();
    expect(await screen.findByText("Check this")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Resolve" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/inbox/comments/c1/resolve"));
  });

  it("a COMMENT_ADDED event refreshes the comments", async () => {
    renderViewer();
    await screen.findByTestId("pdf");
    expect(screen.queryByText("Late note")).not.toBeInTheDocument();

    currentComments = [
      { id: "c9", submissionId: "s1", revisionNo: 1, parentId: null, authorId: "h1", authorName: "Healer",
        authorSide: "HEALER", page: null, x: null, y: null, body: "Late note", createdAt: "2026-11-02T10:00:00Z",
        resolvedAt: null },
    ];
    act(() => {
      FakeEventSource.instances[0].listeners["review"](
        new MessageEvent("review", {
          data: JSON.stringify({ submissionId: "s1", type: "COMMENT_ADDED", revisionNo: 1 }),
        })
      );
    });

    expect(await screen.findByText("Late note")).toBeInTheDocument();
  });

  it("approveSendsLatestRevisionNo and refetches the submission", async () => {
    const user = userEvent.setup();
    currentLatest = 2;
    vi.mocked(api.post).mockResolvedValue({ data: { ...submission(2), status: "APPROVED" } });
    renderViewer();
    await screen.findByTestId("pdf");

    const approveButton = screen.getByRole("button", { name: "Approve" });
    await waitFor(() => expect(approveButton).toBeEnabled());
    await user.click(approveButton);

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/inbox/s1/approve", { revisionNo: 2 })
    );
  });

  it("request changes sends the revision on screen", async () => {
    const user = userEvent.setup();
    vi.mocked(api.post).mockResolvedValue({ data: { ...submission(1), status: "CHANGES_REQUESTED" } });
    renderViewer();
    await screen.findByTestId("pdf");

    await user.click(screen.getByRole("button", { name: "Request changes" }));

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/inbox/s1/request-changes", { revisionNo: 1 })
    );
  });

  it("staleApprove409_showsBanner with the server's reason", async () => {
    const user = userEvent.setup();
    vi.mocked(api.post).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { detail: "A newer version was submitted. Refresh to review it." } },
    });
    renderViewer();
    await screen.findByTestId("pdf");

    // The newer revision lands on the server before its event reaches this tab.
    currentLatest = 2;
    await user.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByText("A newer version was submitted. Refresh to review it.")).toBeInTheDocument();
    // The refetch after the refusal brings up the refresh banner for the newer version.
    expect(await screen.findByText("New version available — refresh")).toBeInTheDocument();
  });

  it("decisions are only offered on the latest revision of an open submission", async () => {
    currentLatest = 1;
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url.endsWith("/inbox/s1/comments")) return Promise.resolve({ data: [] });
      if (url.endsWith("/inbox/s1")) return Promise.resolve({ data: { ...submission(1), status: "APPROVED" } });
      if (url.endsWith("/inbox")) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: { url: "https://r2/rev1.pdf" } });
    });
    renderViewer();
    await screen.findByTestId("pdf");

    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Request changes" })).toBeDisabled();
  });
});
