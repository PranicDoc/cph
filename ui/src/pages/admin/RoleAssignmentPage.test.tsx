import { readFileSync } from "node:fs";

import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import RoleAssignmentPage from "./RoleAssignmentPage";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import api from "../../lib/axios";
import { toast } from "sonner";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mockedDelete = api.delete as unknown as ReturnType<typeof vi.fn>;
const mockedToastSuccess = toast.success as unknown as ReturnType<typeof vi.fn>;
const mockedToastError = toast.error as unknown as ReturnType<typeof vi.fn>;

const ROLE = { id: "r1", name: "Billing", description: null, grants: [] };

function admHealer(id: string, fullName: string, email: string, role = "HEALER") {
  return {
    id,
    fullName,
    email,
    phone: null,
    role,
    healingLevel: null,
    active: true,
    createdAt: "2026-01-01T00:00:00Z",
    hasPassword: true,
    hasGoogleLinked: false,
  };
}

const HEALERS = [
  admHealer("u1", "Alice Assigned", "alice@corp.com"),
  admHealer("u2", "", "nameless@corp.com"),
  admHealer("h-bob", "Bob Builder", "bob@corp.com"),
  admHealer("h-zoe", "", "zoe@corp.com"),
  admHealer("h-sup", "Sam Super", "sam@root.com", "SUPADM"),
];

function assignedStub() {
  return [
    {
      userId: "u1",
      name: "Alice Assigned",
      email: "alice@corp.com",
      assignedAt: "2026-02-01T10:00:00Z",
    },
    {
      userId: "u2",
      name: null,
      email: "nameless@corp.com",
      assignedAt: "2026-02-02T10:00:00Z",
    },
  ];
}

function wireGet({
  assigned = assignedStub() as unknown,
  assignedError = false,
}: { assigned?: unknown; assignedError?: boolean } = {}) {
  mockedGet.mockImplementation((url: string) => {
    if (url === "/api/admin/roles/r1") return Promise.resolve({ data: ROLE });
    if (url === "/api/v1/healers") return Promise.resolve({ data: HEALERS });
    if (url === "/api/admin/roles/r1/users") {
      return assignedError
        ? Promise.reject(new Error("boom"))
        : Promise.resolve({ data: assigned });
    }
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

function renderPage() {
  return renderWithDataRouter(
    [
      { path: "/admin/roles", element: <div>ROLES LIST</div> },
      { path: "/admin/roles/:id/users", element: <RoleAssignmentPage /> },
    ],
    { initialEntries: ["/admin/roles/r1/users"] },
  );
}

beforeEach(() => {
  mockedGet.mockReset();
  mockedPost.mockReset();
  mockedDelete.mockReset();
  mockedToastSuccess.mockReset();
  mockedToastError.mockReset();
  mockedPost.mockResolvedValue({ data: {} });
  mockedDelete.mockResolvedValue({ data: {} });
});

describe("RoleAssignmentPage — assigned users list + add/remove", () => {
  it("requests the assigned users and renders a row per user with name, email and a locale date", async () => {
    wireGet();
    renderPage();

    expect(await screen.findByText("Alice Assigned")).toBeInTheDocument();
    await waitFor(() =>
      expect(mockedGet).toHaveBeenCalledWith("/api/admin/roles/r1/users"),
    );

    const aliceRow = screen.getByText("Alice Assigned").closest("tr") as HTMLElement;
    expect(within(aliceRow).getByText("alice@corp.com")).toBeInTheDocument();
    expect(
      within(aliceRow).getByText(
        new Date("2026-02-01T10:00:00Z").toLocaleDateString(),
      ),
    ).toBeInTheDocument();
  });

  it("falls back to the email in the Name column when a user has no name", async () => {
    wireGet();
    renderPage();
    await screen.findByText("Alice Assigned");

    // u2 has name: null -> appears twice (Name-column fallback + Email column).
    expect(screen.getAllByText("nameless@corp.com")).toHaveLength(2);
  });

  it("renders the empty state when nobody is assigned", async () => {
    wireGet({ assigned: [] });
    renderPage();

    expect(await screen.findByText("No users assigned")).toBeInTheDocument();
  });

  it("renders the load-error copy with a Retry control", async () => {
    wireGet({ assignedError: true });
    renderPage();

    expect(
      await screen.findByText(
        "Couldn't load the users assigned to this role.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("issues exactly one POST per selected user when the picker is confirmed", async () => {
    const user = userEvent.setup();
    wireGet();
    renderPage();
    await screen.findByText("Alice Assigned");

    await user.click(screen.getByRole("button", { name: "Add users" }));
    // SUPADM excluded from the picker.
    expect(screen.queryByText("Sam Super")).toBeNull();

    await user.click(await screen.findByText("Bob Builder"));
    await user.click(screen.getByText("zoe@corp.com"));
    await user.click(screen.getByRole("button", { name: "Assign users" }));

    await waitFor(() => expect(mockedPost).toHaveBeenCalledTimes(2));
    expect(
      mockedPost.mock.calls.every(
        (call) => call[0] === "/api/admin/roles/r1/users",
      ),
    ).toBe(true);
    const bodies = mockedPost.mock.calls.map((call) => call[1]);
    expect(bodies).toEqual(
      expect.arrayContaining([{ userId: "h-bob" }, { userId: "h-zoe" }]),
    );
  });

  it("opens the remove confirm with the user's name and does not DELETE until confirmed", async () => {
    const user = userEvent.setup();
    wireGet();
    renderPage();
    await screen.findByText("Alice Assigned");

    await user.click(
      screen.getByRole("button", { name: "Remove Alice Assigned" }),
    );

    expect(
      await screen.findByText("Remove Alice Assigned from this role?"),
    ).toBeInTheDocument();
    expect(mockedDelete).not.toHaveBeenCalled();
  });

  it("removes a user on confirm and shows a success toast naming the role", async () => {
    const user = userEvent.setup();
    wireGet();
    renderPage();
    await screen.findByRole("heading", { name: /Billing/ });
    await screen.findByText("Alice Assigned");

    await user.click(
      screen.getByRole("button", { name: "Remove Alice Assigned" }),
    );
    await user.click(await screen.findByRole("button", { name: "Remove" }));

    await waitFor(() =>
      expect(mockedDelete).toHaveBeenCalledWith("/api/admin/roles/r1/users/u1"),
    );
    await waitFor(() =>
      expect(mockedToastSuccess).toHaveBeenCalledWith(
        'Alice Assigned removed from "Billing".',
      ),
    );
  });

  it("blocks a 409 self-lockout removal: dialog stays open, detail in role=alert, row kept, no success toast", async () => {
    const user = userEvent.setup();
    wireGet();
    mockedDelete.mockRejectedValue({
      response: {
        status: 409,
        data: { detail: "This change would leave nobody able to manage roles." },
      },
    });
    renderPage();
    await screen.findByText("Alice Assigned");

    await user.click(
      screen.getByRole("button", { name: "Remove Alice Assigned" }),
    );
    await user.click(await screen.findByRole("button", { name: "Remove" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "This change would leave nobody able to manage roles.",
    );
    expect(
      screen.getByText("Remove Alice Assigned from this role?"),
    ).toBeInTheDocument();
    expect(screen.getByText("Alice Assigned")).toBeInTheDocument();
    expect(mockedToastSuccess).not.toHaveBeenCalled();
  });

  it("on a non-409 removal error: closes the dialog and calls toast.error", async () => {
    const user = userEvent.setup();
    wireGet();
    mockedDelete.mockRejectedValue({
      response: { status: 500, data: { detail: "kaboom" } },
    });
    renderPage();
    await screen.findByText("Alice Assigned");

    await user.click(
      screen.getByRole("button", { name: "Remove Alice Assigned" }),
    );
    await user.click(await screen.findByRole("button", { name: "Remove" }));

    await waitFor(() => expect(mockedToastError).toHaveBeenCalled());
    expect(
      screen.queryByText("Remove Alice Assigned from this role?"),
    ).not.toBeInTheDocument();
  });

  it("does not depend on the unsaved-changes guard (page is not discard-guarded)", () => {
    const src = readFileSync(
      "src/pages/admin/RoleAssignmentPage.tsx",
      "utf8",
    );
    expect(src).not.toContain("useUnsavedChangesPrompt");
  });
});
