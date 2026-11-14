import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import ReviewViewer from "./ReviewViewer";
import api from "../../lib/axios";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), defaults: { baseURL: "" } },
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
function mockApi() {
  vi.mocked(api.get).mockImplementation((url: string) => {
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
    vi.stubGlobal("EventSource", FakeEventSource);
    mockApi();
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
});
