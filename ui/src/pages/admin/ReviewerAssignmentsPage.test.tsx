import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import ReviewerAssignmentsPage from "./ReviewerAssignmentsPage";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "../../lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPut = api.put as unknown as ReturnType<typeof vi.fn>;
const mDelete = api.delete as unknown as ReturnType<typeof vi.fn>;

const healer = (id: string, fullName: string, active = true) => ({
  id, fullName, email: `${fullName.toLowerCase()}@x.org`, phone: null, role: "HEALER",
  healingLevel: null, active, createdAt: "2026-01-01T00:00:00Z", hasPassword: true, hasGoogleLinked: false,
});

const HEALERS = [healer("h1", "Asha"), healer("h2", "Ravi"), healer("h3", "Gone", false)];
// Ravi (h2) is the only instructor holding CASE:REVIEW, and he is also a healer in the list.
const ELIGIBLE = [{ id: "h2", fullName: "Ravi", email: "ravi@x.org" }];

function stubApi(assignments: unknown[]) {
  mGet.mockImplementation((url: string) => {
    if (url === "/api/v1/healers") return Promise.resolve({ data: HEALERS });
    if (url === "/api/v1/admin/reviewer-assignments") return Promise.resolve({ data: assignments });
    if (url === "/api/v1/admin/reviewer-assignments/eligible-reviewers") return Promise.resolve({ data: ELIGIBLE });
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

const renderPage = () => renderWithDataRouter([{ path: "/", element: <ReviewerAssignmentsPage /> }]);

describe("ReviewerAssignmentsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mPut.mockResolvedValue({ data: { healerId: "h1", healerName: "Asha", reviewerId: "h2", reviewerName: "Ravi" } });
    mDelete.mockResolvedValue({ data: "" });
  });

  it("listsHealersWithReviewer", async () => {
    stubApi([{ healerId: "h1", healerName: "Asha", reviewerId: "h2", reviewerName: "Ravi" }]);
    renderPage();

    expect(await screen.findByTestId("reviewer-h1")).toHaveTextContent("Reviewer: Ravi");
    expect(screen.getByTestId("reviewer-h2")).toHaveTextContent("No instructor linked");
    // Inactive healers are not listed.
    expect(screen.queryByText("Gone")).not.toBeInTheDocument();
  });

  it("assignPicksFromEligibleOnly", async () => {
    stubApi([]);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("combobox", { name: "Instructor for Asha" }));
    // Only the eligible instructor is offered; Asha and the inactive healer are not.
    const options = await screen.findAllByRole("option");
    expect(options).toHaveLength(1);
    await user.click(screen.getByRole("option", { name: /Ravi/ }));

    await waitFor(() =>
      expect(mPut).toHaveBeenCalledWith("/api/v1/admin/reviewer-assignments/h1", { reviewerId: "h2" })
    );
  });

  it("selfLinkNotOffered", async () => {
    stubApi([]);
    const user = userEvent.setup();
    renderPage();

    // Ravi is the only eligible instructor, but he cannot review himself: nothing to pick.
    const ravi = await screen.findByRole("combobox", { name: "Instructor for Ravi" });
    expect(ravi).toBeDisabled();
    await user.click(ravi);
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });

  it("unlinks an assigned healer", async () => {
    stubApi([{ healerId: "h1", healerName: "Asha", reviewerId: "h2", reviewerName: "Ravi" }]);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Unlink instructor from Asha" }));
    await waitFor(() => expect(mDelete).toHaveBeenCalledWith("/api/v1/admin/reviewer-assignments/h1"));
  });
});

describe("Reviewer Links nav item", () => {
  it("is shown only to a user holding CREDIT_ACCOUNTS:ADMIN (SUPADM)", async () => {
    const { adminNavItems } = await import("../../config/adminNavItems");
    const { visibleNavItems } = await import("../../lib/adminAccess");
    const path = "/admin/reviewer-assignments";
    const ordinary = { grants: { ROLES: ["READ"], CREDIT_ACCOUNTS: ["READ"], CASE: ["READ", "UPDATE"] } };
    const supadm = { grants: { CREDIT_ACCOUNTS: ["READ", "ADMIN"] } };
    expect(visibleNavItems(ordinary, adminNavItems).some((i) => i.path === path)).toBe(false);
    expect(visibleNavItems(supadm, adminNavItems).some((i) => i.path === path)).toBe(true);
  });
});
