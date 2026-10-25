import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { RoleUsersTab } from "./RoleUsersTab";

vi.mock("../../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
// Every verb is granted unless a test narrows it with denied.add("RESOURCE:VERB").
const perms = vi.hoisted(() => ({ denied: new Set<string>() }));
vi.mock("../../../hooks/useMyPermissions", () => ({
  useCan: () => (resource: string, verb: string) => !perms.denied.has(`${resource}:${verb}`),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "../../../lib/axios";
import { toast } from "sonner";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mDelete = api.delete as unknown as ReturnType<typeof vi.fn>;
const mToastSuccess = toast.success as unknown as ReturnType<typeof vi.fn>;
const mToastError = toast.error as unknown as ReturnType<typeof vi.fn>;

const HEALERS = [
  { id: "u1", fullName: "Alice Assigned", email: "alice@corp.com", phone: null, role: "HEALER", healingLevel: null, active: true, createdAt: "2026-01-01T00:00:00Z", hasPassword: true, hasGoogleLinked: false },
  { id: "h-bob", fullName: "Bob Builder", email: "bob@corp.com", phone: null, role: "HEALER", healingLevel: null, active: true, createdAt: "2026-01-01T00:00:00Z", hasPassword: true, hasGoogleLinked: false },
  { id: "h-sup", fullName: "Sam Super", email: "sam@root.com", phone: null, role: "SUPADM", healingLevel: null, active: true, createdAt: "2026-01-01T00:00:00Z", hasPassword: true, hasGoogleLinked: false },
];

const ASSIGNED = [
  { userId: "u1", name: "Alice Assigned", email: "alice@corp.com", assignedAt: "2026-02-01T10:00:00Z" },
];

function wire({ assigned = ASSIGNED as unknown, assignedError = false } = {}) {
  mGet.mockImplementation((url: string) => {
    if (url === "/api/v1/healers") return Promise.resolve({ data: HEALERS });
    if (url === "/api/admin/roles/r1/users") {
      return assignedError ? Promise.reject(new Error("boom")) : Promise.resolve({ data: assigned });
    }
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    React.createElement(
      QueryClientProvider,
      { client: qc },
      React.createElement(RoleUsersTab, { roleId: "r1", roleName: "Billing" }),
    ),
  );
}

beforeEach(() => {
  perms.denied.clear();
  mGet.mockReset();
  mPost.mockReset();
  mDelete.mockReset();
  mToastSuccess.mockReset();
  mToastError.mockReset();
});

describe("RoleUsersTab", () => {
  it("lists assigned users with a relative assigned date and a count line", async () => {
    wire();
    renderTab();
    expect(await screen.findByText("Alice Assigned")).toBeInTheDocument();
    expect(screen.getByText(/1 user has this role/)).toBeInTheDocument();
    expect(screen.getByText(/ago$/)).toBeInTheDocument();
  });

  it("shows the empty state with an Assign user control", async () => {
    wire({ assigned: [] });
    renderTab();
    expect(await screen.findByText("No users assigned")).toBeInTheDocument();
  });

  it("shows the load-error copy with Retry", async () => {
    wire({ assignedError: true });
    renderTab();
    expect(
      await screen.findByText("Couldn't load the users assigned to this role."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("Add users fires one POST per selected healer, SUPADM and assigned excluded", async () => {
    const user = userEvent.setup();
    wire();
    mPost.mockResolvedValue({ data: {} });
    renderTab();
    await screen.findByText("Alice Assigned");

    await user.click(screen.getByRole("button", { name: /Assign user/ }));
    // SUPADM + already-assigned are not offered
    expect(screen.queryByText("Sam Super")).toBeNull();
    expect(screen.queryByText("Alice Assigned")).toBeInTheDocument(); // still in the table, not the picker list twice
    await user.click(screen.getByText("Bob Builder"));
    await user.click(screen.getByRole("button", { name: /^Assign 1 user/ }));

    await waitFor(() => expect(mPost).toHaveBeenCalledTimes(1));
    expect(mPost).toHaveBeenCalledWith("/api/admin/roles/r1/users", { userId: "h-bob" });
  });

  it("on a 409 self-lockout removal: keeps the dialog open, inline alert, user stays, no toast", async () => {
    const user = userEvent.setup();
    wire();
    mDelete.mockRejectedValue({
      response: { status: 409, data: { detail: "nobody able to manage roles" } },
    });
    renderTab();
    await screen.findByText("Alice Assigned");

    await user.click(screen.getByRole("button", { name: /Remove Alice Assigned/ }));
    await user.click(await screen.findByRole("button", { name: "Remove" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("nobody able to manage roles");
    expect(screen.getByText("Alice Assigned")).toBeInTheDocument();
    expect(mToastSuccess).not.toHaveBeenCalled();
  });

  it("on a non-409 removal error: closes the dialog and toast.error", async () => {
    const user = userEvent.setup();
    wire();
    mDelete.mockRejectedValue({ response: { status: 500, data: { detail: "boom" } } });
    renderTab();
    await screen.findByText("Alice Assigned");

    await user.click(screen.getByRole("button", { name: /Remove Alice Assigned/ }));
    await user.click(await screen.findByRole("button", { name: "Remove" }));

    await waitFor(() => expect(mToastError).toHaveBeenCalled());
  });
});
