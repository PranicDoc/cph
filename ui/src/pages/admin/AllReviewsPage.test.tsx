import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import AllReviewsPage from "./AllReviewsPage";
import api from "../../lib/axios";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), put: vi.fn(), delete: vi.fn(), defaults: { baseURL: "" } },
  refreshSession: vi.fn(),
}));
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: "sa", role: "SUPADM" }, isAuthenticated: true }),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const row = (over: Record<string, unknown>) => ({
  submissionId: "s1", healerId: "h1", healerName: "Meera Nair", reviewerId: "r1", reviewerName: "Asha Rao",
  caseTitle: "Chronic migraine", latestRevisionNo: 2, status: "PENDING_REVIEW",
  submittedAt: "2026-11-01T10:00:00Z", ...over,
});

const ROWS = [
  row({}),
  row({ submissionId: "s2", healerId: "h2", healerName: "Kiran Rao", reviewerId: null, reviewerName: null,
    caseTitle: "Joint stiffness", status: "APPROVED" }),
  row({ submissionId: "s3", healerId: "h3", healerName: "Sana Iqbal", reviewerId: "gone", reviewerName: null,
    caseTitle: "Insomnia", status: "CHANGES_REQUESTED" }),
];

const candidate = (id: string, fullName: string) => ({
  id, fullName, email: `${id}@x.test`, hasReviewPermission: true, superAdmin: false,
});
const CANDIDATES = [candidate("r1", "Asha Rao"), candidate("r2", "Dev Patel")];

let candidatesResponse: Promise<unknown>;

function mockApi() {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url === "/api/v1/admin/reviews") return Promise.resolve({ data: ROWS });
    if (url === "/api/v1/admin/reviewer-assignments") {
      return Promise.resolve({
        data: [{ healerId: "h1", healerName: "Meera Nair", reviewerId: "r1", reviewerName: "Asha Rao", approvedCount: 0 }],
      });
    }
    if (url === "/api/v1/admin/reviewer-assignments/eligible-reviewers") return candidatesResponse as never;
    return Promise.reject(new Error("unexpected " + url));
  });
}

const reviewCalls = () => vi.mocked(api.get).mock.calls.filter((c) => c[0] === "/api/v1/admin/reviews");
const paramsSent = () => reviewCalls().map((c) => (c[1] as { params: unknown }).params);

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AllReviewsPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const rowFor = async (title: string) => (await screen.findByText(title)).closest("tr")!;

describe("AllReviewsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    candidatesResponse = Promise.resolve({ data: CANDIDATES });
    mockApi();
  });

  it("shows the super admin banner and the columns", async () => {
    renderPage();
    await rowFor("Chronic migraine");
    expect(screen.getByRole("heading", { name: "All reviews" })).toBeInTheDocument();
    expect(screen.getByText("Every submitted case sheet, whoever it is assigned to.")).toBeInTheDocument();
    expect(screen.getByText("You are acting as super admin.")).toBeInTheDocument();
    expect(screen.getByText(/recorded under your name in the audit log/)).toBeInTheDocument();
    for (const name of ["Case", "Healer", "Assigned reviewer", "Submitted", "Status"]) {
      expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
    }
    expect(screen.queryByText(/Proposed/)).not.toBeInTheDocument();
  });

  it("says there is no instructor only when there is no reviewer id", async () => {
    renderPage();
    expect(within(await rowFor("Joint stiffness")).getByText("No instructor linked")).toBeInTheDocument();
    const deleted = within(await rowFor("Insomnia"));
    expect(deleted.getByText("Unknown")).toBeInTheDocument();
    expect(deleted.queryByText("No instructor linked")).not.toBeInTheDocument();
    expect(within(await rowFor("Chronic migraine")).getByText("Asha Rao")).toBeInTheDocument();
  });

  it("links open submissions as Open review and closed ones as View", async () => {
    renderPage();
    expect(within(await rowFor("Chronic migraine")).getByRole("link", { name: "Open review" }))
      .toHaveAttribute("href", "/reviews/s1");
    expect(within(await rowFor("Insomnia")).getByRole("link", { name: "Open review" }))
      .toHaveAttribute("href", "/reviews/s3");
    expect(within(await rowFor("Joint stiffness")).getByRole("link", { name: "View" }))
      .toHaveAttribute("href", "/reviews/s2");
  });

  it("counts by status and sends the chosen filters to the server", async () => {
    const user = userEvent.setup();
    renderPage();
    await rowFor("Chronic migraine");
    const filter = within(screen.getByRole("group", { name: "Filter by status" }));
    expect(filter.getByRole("button", { name: "All 3" })).toHaveAttribute("aria-pressed", "true");
    expect(filter.getByRole("button", { name: "Approved 1" })).toBeInTheDocument();

    await user.click(filter.getByRole("button", { name: "Approved 1" }));
    await waitFor(() => expect(paramsSent()).toContainEqual({ status: "APPROVED" }));

    await user.selectOptions(screen.getByRole("combobox", { name: "Reviewer" }), "r1");
    await waitFor(() => expect(paramsSent()).toContainEqual({ status: "APPROVED", reviewerId: "r1" }));

    await user.selectOptions(screen.getByRole("combobox", { name: "Healer" }), "h1");
    await waitFor(() =>
      expect(paramsSent()).toContainEqual({ status: "APPROVED", reviewerId: "r1", healerId: "h1" })
    );
    // The counts follow the people filters, not the status one.
    expect(paramsSent()).toContainEqual({ reviewerId: "r1", healerId: "h1" });
  });

  it("offers each reviewer and healer once in the filters", async () => {
    renderPage();
    await rowFor("Chronic migraine");
    const options = (name: string) =>
      within(screen.getByRole("combobox", { name })).getAllByRole("option").map((o) => o.textContent);
    expect(options("Reviewer")).toEqual(["All", "Asha Rao", "Unknown"]);
    expect(options("Healer")).toEqual(["All", "Kiran Rao", "Meera Nair", "Sana Iqbal"]);
  });

  it("reassigns from the picker, then reloads the list", async () => {
    const user = userEvent.setup();
    vi.mocked(api.put).mockResolvedValue({ data: { healerName: "Meera Nair", reviewerName: "Dev Patel" } });
    renderPage();
    await user.click(within(await rowFor("Chronic migraine")).getByRole("button", { name: /reassign/i }));

    const dialog = within(await screen.findByRole("dialog"));
    expect(dialog.getByText("Assign a reviewer for Meera Nair")).toBeInTheDocument();
    expect(dialog.getByText("This reassigns all of this healer's open reviews.")).toBeInTheDocument();
    expect(await dialog.findByRole("radio", { name: /Asha Rao/ })).toBeChecked();
    const before = reviewCalls().length;

    await user.click(dialog.getByRole("radio", { name: /Dev Patel/ }));
    await user.click(dialog.getByRole("button", { name: "Assign Dev Patel" }));

    await waitFor(() =>
      expect(api.put).toHaveBeenCalledWith("/api/v1/admin/reviewer-assignments/h1", { reviewerId: "r2" })
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() => expect(reviewCalls().length).toBeGreaterThan(before));
  });

  it("returns focus to the button that opened the picker", async () => {
    const user = userEvent.setup();
    renderPage();
    const opener = within(await rowFor("Chronic migraine")).getByRole("button", { name: /reassign/i });
    await user.click(opener);
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(opener).toHaveFocus());
  });

  it("keeps another healer's picker open when an earlier assignment succeeds late", async () => {
    const user = userEvent.setup();
    let finish!: (value: unknown) => void;
    vi.mocked(api.put).mockReturnValue(new Promise((resolve) => (finish = resolve)) as never);
    renderPage();
    await user.click(within(await rowFor("Chronic migraine")).getByRole("button", { name: /reassign/i }));
    let dialog = within(await screen.findByRole("dialog"));
    await user.click(await dialog.findByRole("radio", { name: /Dev Patel/ }));
    await user.click(dialog.getByRole("button", { name: "Assign Dev Patel" }));
    await user.click(dialog.getByRole("button", { name: "Cancel" }));

    await user.click(within(await rowFor("Joint stiffness")).getByRole("button", { name: /reassign/i }));
    await act(async () => finish({ data: { healerName: "Meera Nair", reviewerName: "Dev Patel" } }));

    dialog = within(screen.getByRole("dialog"));
    expect(dialog.getByText("Assign a reviewer for Kiran Rao")).toBeInTheDocument();
  });

  it("does not claim there are no matches while the accounts are loading", async () => {
    const user = userEvent.setup();
    candidatesResponse = new Promise(() => {});
    renderPage();
    await user.click(within(await rowFor("Chronic migraine")).getByRole("button", { name: /reassign/i }));
    const dialog = within(await screen.findByRole("dialog"));
    expect(dialog.getByText("Loading accounts…")).toBeInTheDocument();
    expect(dialog.queryByText("No accounts match your search.")).not.toBeInTheDocument();
  });
});
