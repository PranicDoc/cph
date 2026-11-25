import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { PdfList } from "./PdfList";
import api from "../lib/axios";

const caseReview = vi.hoisted(() => ({ on: true }));
vi.mock("../hooks/useCaseReviewEnabled", () => ({ useCaseReviewEnabled: () => caseReview.on }));
vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() } }));
vi.mock("../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
vi.mock("../hooks/useEventStream", () => ({ useEventStream: () => ({ unreadCount: 0, resetUnread: () => {} }) }));
vi.mock("../context/PatientContext", () => ({
  usePatient: () => ({
    patients: [
      { healingCaseId: "case-a", fullName: "Asha Ready" },
      { healingCaseId: "case-b", fullName: "Bala Approved" },
    ],
  }),
}));

const pdf = (healingCaseId: string) => ({ healingCaseId, fileUrl: `f-${healingCaseId}`, completedAt: "2026-11-25T10:00:00Z" });

function mockApi() {
  vi.mocked(api.get).mockImplementation(async (url: string) => {
    if (url.startsWith("/api/v1/cases/pdfs")) {
      return { data: { data: { content: [pdf("case-a"), pdf("case-b")], positionAt: null } } };
    }
    if (url === "/api/v1/reviews/case-statuses") {
      return { data: [
        { caseId: "case-a", status: "NOT_SUBMITTED", eligible: true, latestRevisionNo: null },
        { caseId: "case-b", status: "APPROVED", eligible: true, latestRevisionNo: 1 },
      ] };
    }
    return { data: [] };
  });
  vi.mocked(api.post).mockResolvedValue({ data: { id: "s1", outcome: "CREATED" } });
}

function renderPage(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  );
  return userEvent.setup();
}

describe("PDF reports: submit for review", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    caseReview.on = true;
    vi.stubGlobal("IntersectionObserver", class { observe() {} disconnect() {} unobserve() {} });
    mockApi();
  });

  it("submits a case from its PDF row, and offers nothing to submit on an approved one", async () => {
    const user = renderPage(<PdfList />);
    const submit = await screen.findByRole("button", { name: /submit for review/i }, { timeout: 5000 });
    expect(submit.closest("tr")).toHaveTextContent("Asha Ready");
    expect(screen.getByText("Approved").closest("tr")).toHaveTextContent("Bala Approved");
    expect(screen.getAllByRole("button", { name: /submit for review/i })).toHaveLength(1);

    await user.click(submit);

    expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/cases/case-a/submit");
  });

  it("shows only the download while case review is off", async () => {
    caseReview.on = false;
    renderPage(<PdfList />);
    expect(await screen.findByText("Asha Ready", {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /download/i }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /submit for review/i })).not.toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalledWith("/api/v1/reviews/case-statuses", expect.anything());
  });
});
