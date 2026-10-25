import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import RoleFormPage from "./RoleFormPage";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("../../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
// Every verb is granted unless a test narrows it with denied.add("RESOURCE:VERB").
const perms = vi.hoisted(() => ({ denied: new Set<string>() }));
vi.mock("../../hooks/useMyPermissions", () => ({
  useCan: () => (resource: string, verb: string) => !perms.denied.has(`${resource}:${verb}`),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "../../lib/axios";
import { toast } from "sonner";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mPut = api.put as unknown as ReturnType<typeof vi.fn>;
const mToastSuccess = toast.success as unknown as ReturnType<typeof vi.fn>;
const mToastError = toast.error as unknown as ReturnType<typeof vi.fn>;

const vb = (value: string, label: string, locked = false) => ({
  value,
  label,
  description: `${label} action`,
  locked,
});

const CATALOG = [
  {
    resource: "ROLES",
    label: "Roles",
    verbs: [vb("READ", "View"), vb("CREATE", "Create"), vb("UPDATE", "Update", true), vb("DELETE", "Delete")],
  },
  { resource: "LEGAL_DOCUMENTS", label: "Legal documents", verbs: [vb("READ", "View"), vb("UPDATE", "Update")] },
  { resource: "PATIENTS", label: "Patients", verbs: [vb("READ", "View"), vb("EXPORT", "Export")] },
];

const ROLE_DETAIL = {
  id: "r1",
  name: "Billing",
  description: "Handles invoices",
  grants: [{ resource: "ROLES", operations: ["READ", "UPDATE"] }],
  createdAt: "2026-07-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

function stubGet({
  catalog = CATALOG as unknown,
  detail = ROLE_DETAIL as unknown,
  catalogError = false,
} = {}) {
  mGet.mockImplementation((url: string) => {
    if (url === "/api/admin/roles/catalog") {
      return catalogError
        ? Promise.reject(new Error("catalog boom"))
        : Promise.resolve({ data: catalog });
    }
    if (url === "/api/v1/healers") return Promise.resolve({ data: [] });
    if (url.endsWith("/users")) return Promise.resolve({ data: [] });
    if (url.startsWith("/api/admin/roles/")) return Promise.resolve({ data: detail });
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

async function addResources(user: ReturnType<typeof userEvent.setup>, labels: string[]) {
  await user.click(screen.getByRole("button", { name: /Add resource/ }));
  for (const label of labels) {
    await user.click(await screen.findByText(label));
  }
  await user.click(screen.getByRole("button", { name: /^Add \d+ resource/ }));
}

/** Click the footer CTA, then confirm in the review modal. */
async function reviewAndSave(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: /Review & save/ }));
  await user.click(await screen.findByRole("button", { name: /Confirm & save/ }));
}

function renderForm(path: string) {
  return renderWithDataRouter(
    [
      { path: "/admin/roles", element: <div>ROLES LIST</div> },
      { path: "/admin/roles/new", element: <RoleFormPage /> },
      { path: "/admin/roles/:id/edit", element: <RoleFormPage /> },
    ],
    { initialEntries: [path] },
  );
}

beforeEach(() => {
  perms.denied.clear();
  mGet.mockReset();
  mPost.mockReset();
  mPut.mockReset();
  mToastSuccess.mockReset();
  mToastError.mockReset();
});

describe("RoleFormPage — shell, gating, prefill, validation", () => {
  it("renders the Create role heading and an empty form once the catalog resolves", async () => {
    stubGet();
    renderForm("/admin/roles/new");
    expect(await screen.findByRole("heading", { name: "Create role" })).toBeInTheDocument();
    expect(await screen.findByLabelText("Role name")).toHaveValue("");
    expect(screen.getByLabelText("Description")).toHaveValue("");
  });

  it("does not render the form while the catalog query is pending", async () => {
    mGet.mockImplementation(() => new Promise(() => {}));
    renderForm("/admin/roles/new");
    await waitFor(() => expect(screen.getByTestId("role-form-skeleton")).toBeInTheDocument());
    expect(screen.queryByLabelText("Role name")).toBeNull();
  });

  it("renders the catalog-error copy with a Retry and no Name field", async () => {
    stubGet({ catalogError: true });
    renderForm("/admin/roles/new");
    expect(await screen.findByText(/Couldn't load the permission catalog/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Role name")).toBeNull();
  });

  it("pre-fills name, description and saved verbs in edit mode, and shows the tabs", async () => {
    stubGet();
    renderForm("/admin/roles/r1/edit");
    expect(await screen.findByRole("heading", { name: "Edit role" })).toBeInTheDocument();
    expect(await screen.findByLabelText("Role name")).toHaveValue("Billing");
    expect(screen.getByLabelText("Description")).toHaveValue("Handles invoices");
    // Roles is the only added resource -> selected -> its READ switch is on.
    expect(screen.getByRole("switch", { name: "View on Roles" })).toHaveAttribute("data-state", "checked");
    expect(screen.getByRole("button", { name: "Permissions" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Users assigned" })).toBeInTheDocument();
  });

  it("adds a picked resource to the grid with no verbs on", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await screen.findByLabelText("Role name");
    await addResources(user, ["Patients"]);
    expect(screen.getByRole("switch", { name: "View on Patients" })).toHaveAttribute("data-state", "unchecked");
  });

  it("removes a resource and its verb group", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await screen.findByLabelText("Role name");
    await addResources(user, ["Patients"]);
    await user.click(screen.getByRole("button", { name: "Remove Patients" }));
    expect(screen.queryByRole("switch", { name: "View on Patients" })).toBeNull();
  });

  it("blocks an empty-name submit with an inline error and no request", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await screen.findByLabelText("Role name");
    await addResources(user, ["Patients"]);
    await user.click(screen.getByRole("switch", { name: "View on Patients" }));
    await user.click(screen.getByRole("button", { name: /Review & save/ }));
    expect(await screen.findByText("Role name is required")).toBeInTheDocument();
    expect(mPost).not.toHaveBeenCalled();
  });

  it("blocks a no-resource submit with the resource error and no request", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "X");
    await user.click(screen.getByRole("button", { name: /Review & save/ }));
    expect(await screen.findByText(/A role with no resources can't grant any access/)).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(/things? need/i);
    expect(mPost).not.toHaveBeenCalled();
  });
});

describe("RoleFormPage — review modal + save flow", () => {
  it("submit opens a review modal listing granted permissions, and cancelling it fires no request", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "Reviewer");
    await addResources(user, ["Patients"]);
    await user.click(screen.getByRole("switch", { name: "View on Patients" }));
    await user.click(screen.getByRole("button", { name: /Review & save/ }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Review "Reviewer"/)).toBeInTheDocument();
    expect(within(dialog).getByText("View")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Back to edit" }));
    expect(mPost).not.toHaveBeenCalled();
  });

  it("confirms the review, posts only granting resources, toasts and returns to the list", async () => {
    const user = userEvent.setup();
    stubGet();
    mPost.mockResolvedValue({ data: { id: "new" } });
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "Reviewer");
    await addResources(user, ["Patients", "Legal documents"]);
    // grant a verb on Patients only; leave Legal documents at zero verbs
    await user.click(screen.getByRole("button", { name: /^Patients/ }));
    await user.click(screen.getByRole("switch", { name: "View on Patients" }));
    await reviewAndSave(user);

    await waitFor(() => expect(mPost).toHaveBeenCalledTimes(1));
    expect(mPost).toHaveBeenCalledWith("/api/admin/roles", {
      name: "Reviewer",
      description: null,
      grants: [{ resource: "PATIENTS", operations: ["READ"] }],
    });
    expect(mToastSuccess).toHaveBeenCalledWith('"Reviewer" is ready to assign to users.');
    expect(await screen.findByText("ROLES LIST")).toBeInTheDocument();
  });

  it("edit save PUTs the full replacement grant set and toasts the saved copy", async () => {
    const user = userEvent.setup();
    stubGet();
    mPut.mockResolvedValue({ data: ROLE_DETAIL });
    renderForm("/admin/roles/r1/edit");
    await screen.findByLabelText("Role name");
    await user.click(screen.getByRole("switch", { name: "Create on Roles" }));
    await reviewAndSave(user);

    await waitFor(() => expect(mPut).toHaveBeenCalledTimes(1));
    const [url, body] = mPut.mock.calls[0];
    expect(url).toBe("/api/admin/roles/r1");
    expect(body.grants[0].resource).toBe("ROLES");
    expect(body.grants[0].operations.sort()).toEqual(["CREATE", "READ", "UPDATE"]);
    expect(mToastSuccess).toHaveBeenCalledWith('Role "Billing" saved.');
  });

  it("renders a duplicate-name 400 inline under Name without navigating or toasting success", async () => {
    const user = userEvent.setup();
    stubGet();
    mPost.mockRejectedValue({
      response: { status: 400, data: { detail: "A role named 'Reviewer' already exists." } },
    });
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "Reviewer");
    await addResources(user, ["Patients"]);
    await user.click(screen.getByRole("switch", { name: "View on Patients" }));
    await reviewAndSave(user);

    expect(await screen.findByText("A role named 'Reviewer' already exists.")).toBeInTheDocument();
    expect(mToastSuccess).not.toHaveBeenCalled();
    expect(screen.queryByText("ROLES LIST")).toBeNull();
  });

  it("renders a self-lockout 409 in a footer alert and keeps the edited form", async () => {
    const user = userEvent.setup();
    stubGet();
    mPut.mockRejectedValue({
      response: {
        status: 409,
        data: { type: "errors/self-lockout", detail: "This change would leave nobody able to manage roles." },
      },
    });
    renderForm("/admin/roles/r1/edit");
    await screen.findByLabelText("Role name");
    await user.click(screen.getByRole("switch", { name: "Create on Roles" }));
    await reviewAndSave(user);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("This change would leave nobody able to manage roles.");
    expect(mToastSuccess).not.toHaveBeenCalled();
    expect(screen.queryByText("ROLES LIST")).toBeNull();
  });

  it("toasts a generic error on a 500 and does not navigate", async () => {
    const user = userEvent.setup();
    stubGet();
    mPost.mockRejectedValue({ response: { status: 500, data: { detail: "boom" } } });
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "Reviewer");
    await addResources(user, ["Patients"]);
    await user.click(screen.getByRole("switch", { name: "View on Patients" }));
    await reviewAndSave(user);

    await waitFor(() => expect(mToastError).toHaveBeenCalled());
    expect(screen.queryByText("ROLES LIST")).toBeNull();
  });
});

describe("RoleFormPage — discard-unsaved-changes guard (ROLE-08)", () => {
  it("navigates away immediately when the form is untouched", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await screen.findByLabelText("Role name");
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(await screen.findByText("ROLES LIST")).toBeInTheDocument();
  });

  it("prompts before leaving once the Name field has been edited", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "x");
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(await screen.findByText("Discard unsaved changes?")).toBeInTheDocument();
    expect(screen.queryByText("ROLES LIST")).toBeNull();
  });

  it("completes the navigation on Discard changes", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "x");
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await user.click(await screen.findByRole("button", { name: "Discard changes" }));
    expect(await screen.findByText("ROLES LIST")).toBeInTheDocument();
  });

  it("stays put and preserves the typed value on Keep editing", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "Keep me");
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await user.click(await screen.findByRole("button", { name: "Keep editing" }));
    expect(screen.getByLabelText("Role name")).toHaveValue("Keep me");
    expect(screen.queryByText("ROLES LIST")).toBeNull();
  });

  it("never prompts during a successful save-and-redirect", async () => {
    const user = userEvent.setup();
    stubGet();
    mPost.mockResolvedValue({ data: { id: "new" } });
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Role name"), "Reviewer");
    await addResources(user, ["Patients"]);
    await user.click(screen.getByRole("switch", { name: "View on Patients" }));
    await reviewAndSave(user);
    expect(await screen.findByText("ROLES LIST")).toBeInTheDocument();
    expect(screen.queryByText("Discard unsaved changes?")).toBeNull();
  });
});
