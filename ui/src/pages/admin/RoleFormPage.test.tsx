import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import RoleFormPage from "./RoleFormPage";
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
const mockedPut = api.put as unknown as ReturnType<typeof vi.fn>;
const mockedToastSuccess = toast.success as unknown as ReturnType<typeof vi.fn>;
const mockedToastError = toast.error as unknown as ReturnType<typeof vi.fn>;

const CATALOG = [
  {
    resource: "ROLES",
    label: "Roles",
    verbs: [
      { value: "READ", label: "View" },
      { value: "CREATE", label: "Create" },
      { value: "UPDATE", label: "Update" },
      { value: "DELETE", label: "Delete" },
    ],
  },
  {
    resource: "LEGAL_DOCUMENTS",
    label: "Legal documents",
    verbs: [
      { value: "READ", label: "View" },
      { value: "UPDATE", label: "Update" },
    ],
  },
  {
    resource: "PATIENTS",
    label: "Patients",
    verbs: [
      { value: "READ", label: "View" },
      { value: "EXPORT", label: "Export" },
    ],
  },
];

const ROLE_DETAIL = {
  id: "r1",
  name: "Billing",
  description: "Handles invoices",
  grants: [{ resource: "ROLES", operations: ["READ", "UPDATE"] }],
};

interface StubOptions {
  catalog?: unknown;
  detail?: unknown;
  catalogError?: boolean;
}

function stubGet(options: StubOptions = {}) {
  const {
    catalog = CATALOG,
    detail = ROLE_DETAIL,
    catalogError = false,
  } = options;
  mockedGet.mockImplementation((url: string) => {
    if (url === "/api/admin/roles/catalog") {
      return catalogError
        ? Promise.reject(new Error("catalog boom"))
        : Promise.resolve({ data: catalog });
    }
    if (url.startsWith("/api/admin/roles/")) {
      return Promise.resolve({ data: detail });
    }
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

async function addResources(
  user: ReturnType<typeof userEvent.setup>,
  labels: string[],
) {
  await user.click(screen.getByRole("button", { name: "Add resources" }));
  for (const label of labels) {
    await user.click(await screen.findByText(label));
  }
  await user.click(screen.getByRole("button", { name: "Add selected" }));
}

function renderForm(path: string) {
  return renderWithDataRouter(
    [
      { path: "/admin/roles", element: <div>ROLES LIST</div> },
      { path: "/admin/roles/new", element: <RoleFormPage /> },
      { path: "/admin/roles/:id/edit", element: <RoleFormPage /> },
      { path: "/admin/roles/:id/users", element: <div>USERS ROUTE</div> },
    ],
    { initialEntries: [path] },
  );
}

beforeEach(() => {
  mockedGet.mockReset();
  mockedPost.mockReset();
  mockedPut.mockReset();
  mockedToastSuccess.mockReset();
  mockedToastError.mockReset();
});

describe("RoleFormPage — shell, gating, prefill, validation gate", () => {
  it("renders the Create role heading and an empty form once the catalog resolves", async () => {
    stubGet();
    renderForm("/admin/roles/new");

    expect(
      await screen.findByRole("heading", { name: "Create role" }),
    ).toBeInTheDocument();
    expect(await screen.findByLabelText("Name")).toHaveValue("");
    expect(screen.getByLabelText("Description")).toHaveValue("");
  });

  it("does not render the form while the catalog query is pending", async () => {
    mockedGet.mockImplementation(() => new Promise(() => {}));
    renderForm("/admin/roles/new");

    expect(await screen.findByTestId("role-form-skeleton")).toBeInTheDocument();
    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
  });

  it("renders the catalog-error copy with a Retry and no Name field", async () => {
    stubGet({ catalogError: true });
    renderForm("/admin/roles/new");

    expect(
      await screen.findByText(
        "Couldn't load the permission catalog. The role form needs it to know which permissions each resource allows.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
  });

  it("requests the role and pre-fills name, description and saved verbs in edit mode", async () => {
    stubGet();
    renderForm("/admin/roles/r1/edit");

    expect(await screen.findByLabelText("Name")).toHaveValue("Billing");
    expect(screen.getByLabelText("Description")).toHaveValue("Handles invoices");
    expect(mockedGet).toHaveBeenCalledWith("/api/admin/roles/r1");
    expect(
      screen.getByRole("checkbox", { name: "View on Roles" }),
    ).toBeChecked();
    expect(
      screen.getByRole("checkbox", { name: "Update on Roles" }),
    ).toBeChecked();
    expect(
      screen.getByRole("checkbox", { name: "Create on Roles" }),
    ).not.toBeChecked();
    expect(
      screen.queryByRole("checkbox", { name: /on Legal documents/ }),
    ).not.toBeInTheDocument();
  });

  it("adds a picked resource to the grid with no verbs checked", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await screen.findByLabelText("Name");

    await user.click(screen.getByRole("button", { name: "Add resources" }));
    await user.click(await screen.findByText("Legal documents"));
    await user.click(screen.getByRole("button", { name: "Add selected" }));

    const box = await screen.findByRole("checkbox", {
      name: "View on Legal documents",
    });
    expect(box).not.toBeChecked();
  });

  it("removes a resource and its verb group", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/r1/edit");
    await screen.findByRole("checkbox", { name: "View on Roles" });

    await user.click(screen.getByRole("button", { name: "Remove Roles" }));

    expect(
      screen.queryByRole("checkbox", { name: "View on Roles" }),
    ).not.toBeInTheDocument();
  });

  it("blocks an empty-name submit with an inline error and no request", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await screen.findByLabelText("Name");

    await user.click(screen.getByRole("button", { name: "Create role" }));

    expect(await screen.findByText("Enter a role name.")).toBeInTheDocument();
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("blocks a no-resource submit with the resource error and no request", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");

    await user.click(screen.getByRole("button", { name: "Create role" }));

    expect(
      await screen.findByText("Add at least one resource before saving."),
    ).toBeInTheDocument();
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("blocks an edit save that strips every resource and issues no put", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/r1/edit");
    await screen.findByRole("checkbox", { name: "View on Roles" });

    await user.click(screen.getByRole("button", { name: "Remove Roles" }));
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(
      await screen.findByText("Add at least one resource before saving."),
    ).toBeInTheDocument();
    expect(mockedPut).not.toHaveBeenCalled();
  });
});

describe("RoleFormPage — save flow, toast, inline 400 / 409", () => {
  it("posts only resources with at least one verb, dropping the zero-verb resource", async () => {
    const user = userEvent.setup();
    stubGet();
    mockedPost.mockResolvedValue({ data: { id: "r9" } });
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");
    await addResources(user, ["Legal documents", "Patients"]);
    await user.click(
      await screen.findByRole("checkbox", { name: "View on Legal documents" }),
    );

    await user.click(screen.getByRole("button", { name: "Create role" }));

    await waitFor(() => expect(mockedPost).toHaveBeenCalled());
    expect(mockedPost).toHaveBeenCalledWith("/api/admin/roles", {
      name: "Auditors",
      description: null,
      grants: [{ resource: "LEGAL_DOCUMENTS", operations: ["READ"] }],
    });
  });

  it("names the dropped zero-verb resource when a granting resource remains", async () => {
    const user = userEvent.setup();
    stubGet();
    // Hangs the request so the on-submit note stays visible for the assertion.
    mockedPost.mockImplementation(() => new Promise(() => {}));
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");
    await addResources(user, ["Legal documents", "Patients"]);
    await user.click(
      await screen.findByRole("checkbox", { name: "View on Legal documents" }),
    );

    await user.click(screen.getByRole("button", { name: "Create role" }));

    expect(
      await screen.findByText(/No permissions were toggled for Patients/),
    ).toBeInTheDocument();
  });

  it("toasts the created copy and returns to the roles list on a successful create", async () => {
    const user = userEvent.setup();
    stubGet();
    mockedPost.mockResolvedValue({ data: { id: "r9" } });
    const { router } = renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");
    await addResources(user, ["Legal documents"]);
    await user.click(
      await screen.findByRole("checkbox", { name: "View on Legal documents" }),
    );

    await user.click(screen.getByRole("button", { name: "Create role" }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/admin/roles"),
    );
    expect(mockedToastSuccess).toHaveBeenCalledWith('Role "Auditors" created.');
  });

  it("toasts the saved copy and puts the full replacement grant set on a successful edit", async () => {
    const user = userEvent.setup();
    stubGet();
    mockedPut.mockResolvedValue({ data: { id: "r1" } });
    const { router } = renderForm("/admin/roles/r1/edit");
    await user.click(
      await screen.findByRole("checkbox", { name: "Create on Roles" }),
    );

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(mockedPut).toHaveBeenCalled());
    const [url, body] = mockedPut.mock.calls[0] as [
      string,
      { name: string; grants: { resource: string; operations: string[] }[] },
    ];
    expect(url).toBe("/api/admin/roles/r1");
    expect(body.name).toBe("Billing");
    expect(body.grants).toHaveLength(1);
    expect(body.grants[0].resource).toBe("ROLES");
    expect([...body.grants[0].operations].sort()).toEqual([
      "CREATE",
      "READ",
      "UPDATE",
    ]);
    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/admin/roles"),
    );
    expect(mockedToastSuccess).toHaveBeenCalledWith('Role "Billing" saved.');
  });

  it("renders a duplicate-name 400 inline under Name without navigating or toasting success", async () => {
    const user = userEvent.setup();
    stubGet();
    mockedPost.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail:
            "A role with that name already exists. Please choose a different name.",
        },
      },
    });
    const { router } = renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Billing");
    await addResources(user, ["Legal documents"]);
    await user.click(
      await screen.findByRole("checkbox", { name: "View on Legal documents" }),
    );

    await user.click(screen.getByRole("button", { name: "Create role" }));

    expect(
      await screen.findByText(
        "A role with that name already exists. Please choose a different name.",
      ),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/admin/roles/new");
    expect(mockedToastSuccess).not.toHaveBeenCalled();
  });

  it("renders a self-lockout 409 in a footer alert and keeps the edited form dirty", async () => {
    const user = userEvent.setup();
    stubGet();
    mockedPut.mockRejectedValue({
      response: {
        status: 409,
        data: {
          type: "https://pranicdoc.com/errors/self-lockout",
          detail: "This change would leave nobody able to manage roles.",
        },
      },
    });
    const { router } = renderForm("/admin/roles/r1/edit");
    await user.click(
      await screen.findByRole("checkbox", { name: "Create on Roles" }),
    );

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "This change would leave nobody able to manage roles.",
    );
    expect(router.state.location.pathname).toBe("/admin/roles/r1/edit");
    expect(mockedToastSuccess).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Name")).toHaveValue("Billing");
    expect(
      screen.getByRole("checkbox", { name: "Create on Roles" }),
    ).toBeChecked();
  });

  it("toasts a generic error on a 500 and does not navigate", async () => {
    const user = userEvent.setup();
    stubGet();
    mockedPost.mockRejectedValue({
      response: { status: 500, data: { detail: "boom" } },
    });
    const { router } = renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");
    await addResources(user, ["Legal documents"]);
    await user.click(
      await screen.findByRole("checkbox", { name: "View on Legal documents" }),
    );

    await user.click(screen.getByRole("button", { name: "Create role" }));

    await waitFor(() => expect(mockedToastError).toHaveBeenCalled());
    expect(router.state.location.pathname).toBe("/admin/roles/new");
  });
});

describe("RoleFormPage — discard-unsaved-changes guard (ROLE-08)", () => {
  it("navigates away immediately when the form is untouched", async () => {
    const user = userEvent.setup();
    stubGet();
    const { router } = renderForm("/admin/roles/new");
    await screen.findByLabelText("Name");

    await user.click(screen.getByRole("button", { name: "Back to roles" }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/admin/roles"),
    );
    expect(
      screen.queryByText("Discard unsaved changes?"),
    ).not.toBeInTheDocument();
  });

  it("prompts before leaving once the Name field has been edited", async () => {
    const user = userEvent.setup();
    stubGet();
    renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");

    await user.click(screen.getByRole("button", { name: "Back to roles" }));

    expect(
      await screen.findByText("Discard unsaved changes?"),
    ).toBeInTheDocument();
  });

  it("completes the navigation on Discard changes", async () => {
    const user = userEvent.setup();
    stubGet();
    const { router } = renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");
    await user.click(screen.getByRole("button", { name: "Back to roles" }));
    await screen.findByText("Discard unsaved changes?");

    await user.click(screen.getByRole("button", { name: "Discard changes" }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/admin/roles"),
    );
  });

  it("stays put and preserves the typed value on Keep editing", async () => {
    const user = userEvent.setup();
    stubGet();
    const { router } = renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");
    await user.click(screen.getByRole("button", { name: "Back to roles" }));
    await screen.findByText("Discard unsaved changes?");

    await user.click(screen.getByRole("button", { name: "Keep editing" }));

    await waitFor(() =>
      expect(
        screen.queryByText("Discard unsaved changes?"),
      ).not.toBeInTheDocument(),
    );
    expect(router.state.location.pathname).toBe("/admin/roles/new");
    expect(screen.getByLabelText("Name")).toHaveValue("Auditors");
  });

  it("treats a reverted edit as clean — no prompt on navigation", async () => {
    const user = userEvent.setup();
    stubGet();
    const { router } = renderForm("/admin/roles/new");
    const nameInput = await screen.findByLabelText("Name");
    await user.type(nameInput, "abc");
    await user.clear(nameInput);

    await user.click(screen.getByRole("button", { name: "Back to roles" }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/admin/roles"),
    );
    expect(
      screen.queryByText("Discard unsaved changes?"),
    ).not.toBeInTheDocument();
  });

  it("never prompts during a successful save-and-redirect", async () => {
    const user = userEvent.setup();
    stubGet();
    mockedPost.mockResolvedValue({ data: { id: "r9" } });
    const { router } = renderForm("/admin/roles/new");
    await user.type(await screen.findByLabelText("Name"), "Auditors");
    await addResources(user, ["Legal documents"]);
    await user.click(
      await screen.findByRole("checkbox", { name: "View on Legal documents" }),
    );

    await user.click(screen.getByRole("button", { name: "Create role" }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/admin/roles"),
    );
    expect(
      screen.queryByText("Discard unsaved changes?"),
    ).not.toBeInTheDocument();
  });

  it("returns to clean when a verb is toggled on then off in edit mode", async () => {
    const user = userEvent.setup();
    stubGet();
    const { router } = renderForm("/admin/roles/r1/edit");
    const box = await screen.findByRole("checkbox", { name: "Create on Roles" });

    await user.click(box);
    await user.click(box);

    await user.click(screen.getByRole("button", { name: "Back to roles" }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe("/admin/roles"),
    );
    expect(
      screen.queryByText("Discard unsaved changes?"),
    ).not.toBeInTheDocument();
  });
});
