import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import ReviewerAssignmentsPage from "./ReviewerAssignmentsPage";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
// The signed-in user is the super admin Venkat.
vi.mock("../../context/AuthContext", () => ({ useAuth: () => ({ user: { id: "sa" } }) }));

import api from "../../lib/axios";
import { toast } from "sonner";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPut = api.put as unknown as ReturnType<typeof vi.fn>;
const mDelete = api.delete as unknown as ReturnType<typeof vi.fn>;

const healer = (id: string, fullName: string, active = true, role = "HEALER") => ({
  id, fullName, email: `${fullName.toLowerCase()}@x.org`, phone: null, role,
  healingLevel: null, active, createdAt: "2026-01-01T00:00:00Z", hasPassword: true, hasGoogleLinked: false,
});

const HEALERS = [
  healer("h1", "Asha"),
  healer("h2", "Ravi"),
  healer("h3", "Gone", false),
  healer("sa", "Venkat", true, "SUPADM"),
  healer("sa2", "Meera", true, "SUPADM"),
];

const candidate = (id: string, fullName: string, hasReviewPermission: boolean, superAdmin: boolean) => ({
  id, fullName, email: `${fullName.toLowerCase()}@x.org`, hasReviewPermission, superAdmin,
});

// Every active account, as the server returns it. Only Ravi holds the Review permission.
const CANDIDATES = [
  candidate("h1", "Asha", false, false),
  candidate("sa2", "Meera", false, true),
  candidate("h2", "Ravi", true, false),
  candidate("sa", "Venkat", false, true),
];

const link = (healerId: string, healerName: string, reviewerId: string, reviewerName: string, approvedCount = 0) =>
  ({ healerId, healerName, reviewerId, reviewerName, approvedCount });

function stubApi(assignments: unknown[]) {
  mGet.mockImplementation((url: string) => {
    if (url === "/api/v1/healers") return Promise.resolve({ data: HEALERS });
    if (url === "/api/v1/admin/reviewer-assignments") return Promise.resolve({ data: assignments });
    if (url === "/api/v1/admin/reviewer-assignments/eligible-reviewers") return Promise.resolve({ data: CANDIDATES });
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

const renderPage = () => renderWithDataRouter([{ path: "/", element: <ReviewerAssignmentsPage /> }]);

async function openPicker(buttonName: string) {
  const user = userEvent.setup();
  renderPage();
  await user.click(await screen.findByRole("button", { name: buttonName }));
  const dialog = await screen.findByRole("dialog");
  return { user, dialog };
}

/** The picker row (the label wrapping the radio) for a candidate. */
const rowOf = (dialog: HTMLElement, name: RegExp) =>
  within(dialog).getByRole("radio", { name }).closest("label") as HTMLElement;

beforeEach(() => {
  vi.clearAllMocks();
  mPut.mockResolvedValue({ data: link("h1", "Asha", "h2", "Ravi") });
  mDelete.mockResolvedValue({ data: "" });
});

describe("ReviewerAssignmentsPage", () => {
  it("listsHealersWithReviewer", async () => {
    stubApi([link("h1", "Asha", "h2", "Ravi")]);
    renderPage();

    expect(await screen.findByTestId("reviewer-h1")).toHaveTextContent("Ravi");
    expect(screen.getByTestId("reviewer-h2")).toHaveTextContent("Not linked");
    // Inactive healers are not listed.
    expect(screen.queryByText("Gone")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Reviewer links" })).toBeInTheDocument();
    for (const column of ["Healer", "Reviewer", "Approved cases"]) {
      expect(screen.getByRole("columnheader", { name: column })).toBeInTheDocument();
    }
  });

  it("offers Change to a linked healer and Assign to an unlinked one", async () => {
    stubApi([link("h1", "Asha", "h2", "Ravi")]);
    renderPage();

    expect(await screen.findByRole("button", { name: "Change reviewer for Asha" })).toHaveTextContent("Change");
    expect(screen.getByRole("button", { name: "Assign a reviewer for Ravi" })).toHaveTextContent("Assign");
    expect(screen.queryByRole("button", { name: "Unlink instructor from Ravi" })).not.toBeInTheDocument();
  });

  it("unlinks an assigned healer", async () => {
    stubApi([link("h1", "Asha", "h2", "Ravi")]);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Unlink instructor from Asha" }));
    await waitFor(() => expect(mDelete).toHaveBeenCalledWith("/api/v1/admin/reviewer-assignments/h1"));
  });
});

describe("Reviewer picker", () => {
  it("lists accounts without the Review permission, each with its pill", async () => {
    stubApi([]);
    const { dialog } = await openPicker("Assign a reviewer for Asha");

    expect(dialog).toHaveAccessibleName("Assign a reviewer for Asha");
    expect(within(dialog).getByText(/the list is not limited to accounts with the Review permission/i)).toBeInTheDocument();
    expect(within(dialog).getByText(
      "Someone without the Review permission will see the Reviews screen for the healers assigned to them, and nothing else."
    )).toBeInTheDocument();
    expect(within(dialog).getAllByRole("radio")).toHaveLength(3);
    expect(rowOf(dialog, /Ravi/)).toHaveTextContent("Has Review permission");
    // Super admin wins over the permission flag.
    expect(rowOf(dialog, /Venkat/)).toHaveTextContent("Super admin");
    expect(rowOf(dialog, /Venkat/)).not.toHaveTextContent("Review permission");
    expect(within(dialog).getByRole("button", { name: "Assign" })).toBeDisabled();
  });

  it("shows No Review permission for an ordinary account without it", async () => {
    stubApi([]);
    const { dialog } = await openPicker("Assign a reviewer for Ravi");

    expect(rowOf(dialog, /Asha/)).toHaveTextContent("No Review permission");
  });

  it("does not offer an ordinary healer as their own reviewer", async () => {
    stubApi([]);
    const { dialog } = await openPicker("Assign a reviewer for Asha");

    expect(within(dialog).queryByRole("radio", { name: /Asha/ })).not.toBeInTheDocument();
  });

  it("offers the signed-in super admin, marked (you), on their own row and assigns them", async () => {
    stubApi([]);
    const { user, dialog } = await openPicker("Assign a reviewer for Venkat");

    await user.click(within(dialog).getByRole("radio", { name: /Venkat \(you\)/ }));
    await user.click(within(dialog).getByRole("button", { name: "Assign Venkat" }));

    await waitFor(() =>
      expect(mPut).toHaveBeenCalledWith("/api/v1/admin/reviewer-assignments/sa", { reviewerId: "sa" })
    );
  });

  it("offers another super admin as their own reviewer without the (you) label", async () => {
    stubApi([]);
    const { dialog } = await openPicker("Assign a reviewer for Meera");

    expect(rowOf(dialog, /Meera/)).not.toHaveTextContent("(you)");
  });

  it("filters by name or email", async () => {
    stubApi([]);
    const { user, dialog } = await openPicker("Assign a reviewer for Asha");
    const search = within(dialog).getByRole("searchbox", { name: "Search by name or email" });

    await user.type(search, "rav");
    expect(within(dialog).getAllByRole("radio")).toHaveLength(1);
    expect(within(dialog).getByRole("radio", { name: /Ravi/ })).toBeInTheDocument();

    await user.clear(search);
    await user.type(search, "  MEERA@X ");
    expect(within(dialog).getAllByRole("radio")).toHaveLength(1);
    expect(within(dialog).getByRole("radio", { name: /Meera/ })).toBeInTheDocument();

    await user.clear(search);
    await user.type(search, "nobody");
    expect(within(dialog).queryByRole("radio")).not.toBeInTheDocument();
    expect(within(dialog).getByText("No accounts match your search.")).toBeInTheDocument();
  });

  it("assigns the chosen account to another healer, including the signed-in super admin", async () => {
    stubApi([]);
    const { user, dialog } = await openPicker("Assign a reviewer for Asha");

    await user.click(within(dialog).getByRole("radio", { name: /Venkat \(you\)/ }));
    await user.click(within(dialog).getByRole("button", { name: "Assign Venkat" }));

    await waitFor(() =>
      expect(mPut).toHaveBeenCalledWith("/api/v1/admin/reviewer-assignments/h1", { reviewerId: "sa" })
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("preselects the current reviewer on Change and waits for a different choice", async () => {
    stubApi([link("h1", "Asha", "h2", "Ravi")]);
    const { user, dialog } = await openPicker("Change reviewer for Asha");

    expect(within(dialog).getByRole("radio", { name: /Ravi/ })).toBeChecked();
    expect(within(dialog).getByRole("button", { name: "Assign Ravi" })).toBeDisabled();

    await user.click(within(dialog).getByRole("radio", { name: /Meera/ }));
    expect(within(dialog).getByRole("button", { name: "Assign Meera" })).toBeEnabled();
  });

  it("starts clean when reopened after Cancel", async () => {
    stubApi([]);
    const { user, dialog } = await openPicker("Assign a reviewer for Asha");
    await user.type(within(dialog).getByRole("searchbox"), "rav");
    await user.click(within(dialog).getByRole("radio", { name: /Ravi/ }));
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Assign a reviewer for Ravi" }));
    const next = await screen.findByRole("dialog");
    expect(within(next).getByRole("searchbox")).toHaveValue("");
    expect(within(next).getByRole("button", { name: "Assign" })).toBeDisabled();
    expect(mPut).not.toHaveBeenCalled();
  });

  it("shows the server's error and keeps the dialog open", async () => {
    stubApi([]);
    mPut.mockRejectedValue({ response: { status: 400, data: { detail: "Reviewer must be an active healer" } } });
    const { user, dialog } = await openPicker("Assign a reviewer for Asha");

    await user.click(within(dialog).getByRole("radio", { name: /Ravi/ }));
    await user.click(within(dialog).getByRole("button", { name: "Assign Ravi" }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith("Reviewer must be an active healer", expect.anything())
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});

describe("ReviewerAssignmentsPage approved counts", () => {
  it("showsApprovedCountPerHealer", async () => {
    stubApi([link("h1", "Asha", "h2", "Ravi", 3)]);
    renderPage();

    expect(await screen.findByTestId("approved-h1")).toHaveTextContent(/^3$/);
    // A healer with no link has nothing approved to show.
    expect(screen.queryByTestId("approved-h2")).not.toBeInTheDocument();
  });
});

describe("Reviewer Links nav item", () => {
  it("is shown only to a user holding CREDIT_ACCOUNTS:ADMIN (SUPADM)", async () => {
    const { adminNavItems } = await import("../../config/adminNavItems");
    const { visibleNavItems } = await import("../../lib/adminAccess");
    const path = "/admin/reviewer-assignments";
    const ordinary = { caseReviewEnabled: true, grants: { ROLES: ["READ"], CREDIT_ACCOUNTS: ["READ"], CASE: ["READ", "UPDATE"] } };
    const supadm = { caseReviewEnabled: true, grants: { CREDIT_ACCOUNTS: ["READ", "ADMIN"] } };
    expect(visibleNavItems(ordinary, adminNavItems).some((i) => i.path === path)).toBe(false);
    expect(visibleNavItems(supadm, adminNavItems).some((i) => i.path === path)).toBe(true);
  });
});
