import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import MySubmissions from "./MySubmissions";
import api from "../../lib/axios";

const caseReview = vi.hoisted(() => ({ on: true }));
vi.mock("../../hooks/useCaseReviewEnabled", () => ({ useCaseReviewEnabled: () => caseReview.on }));
vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() } }));
vi.mock("../../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
vi.mock("../../hooks/useEventStream", () => ({ useEventStream: () => ({ unreadCount: 0, resetUnread: () => {} }) }));
vi.mock("../../context/PatientContext", () => ({
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

describe("My submissions: submit from the PDF list", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    caseReview.on = true;
    mockApi();
  });

  it("opens the list of PDFs that can be sent, and submits the chosen one", async () => {
    const user = renderPage(<MySubmissions />);
    await user.click(await screen.findByRole("button", { name: "Submit a case for review" }));

    const dialog = await screen.findByRole("dialog", { name: "Submit a case for review" });
    const submit = await within(dialog).findByRole("button", { name: /^submit for review$/i }, { timeout: 5000 });
    expect(within(dialog).getByText("Asha Ready")).toBeInTheDocument();
    // An approved case has nothing left to submit.
    expect(within(dialog).queryByText("Bala Approved")).not.toBeInTheDocument();

    await user.click(submit);

    expect(api.post).toHaveBeenCalledWith("/api/v1/reviews/cases/case-a/submit");
  });

  it("says what to do when no PDF is waiting to be sent", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) =>
      url.startsWith("/api/v1/cases/pdfs") ? { data: { data: { content: [], positionAt: null } } } : { data: [] });
    const user = renderPage(<MySubmissions />);
    await user.click(await screen.findByRole("button", { name: "Submit a case for review" }));
    expect(await screen.findByText(/generate a case pdf first/i)).toBeInTheDocument();
  });
});
