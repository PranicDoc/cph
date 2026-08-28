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
