import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

const { mockUseUiVersion, mockUseAuth, mockUseMyPermissions } = vi.hoisted(() => ({
  mockUseUiVersion: vi.fn(),
  mockUseAuth: vi.fn(),
  mockUseMyPermissions: vi.fn(),
}));

vi.mock("../../ui-version", () => ({ useUiVersion: () => mockUseUiVersion() }));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => mockUseAuth() }));
vi.mock("../../hooks/useMyPermissions", () => ({ useMyPermissions: () => mockUseMyPermissions() }));
vi.mock("../../components/UserProfile", () => ({ default: () => <button type="button">profile menu</button> }));

import AdminShell from "./AdminShell";
import HealerShell from "./HealerShell";

function renderAt(path: string, element: React.ReactElement) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={element}>
          <Route path="*" element={<div>PAGE</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe("AdminShell", () => {
  beforeEach(() => {
    mockUseUiVersion.mockReturnValue({ resolvedTheme: "dark", setTheme: vi.fn() });
  });

  it("shows a super admin every admin page, grouped, with the Admin crumb", () => {
    mockUseAuth.mockReturnValue({ user: { fullName: "Root", role: "SUPADM" } });
    mockUseMyPermissions.mockReturnValue({ data: { grants: { CREDIT_ACCOUNTS: ["READ"], GLOBAL_CONFIG: ["READ"], PACKAGES: ["READ"], MASTER_DATA_COLORS: ["READ"], HEALER_REG: ["READ"], LEGAL_DOCUMENTS: ["READ"], ROLES: ["READ"] } } });
    renderAt("/admin/packages", <AdminShell />);
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    expect(within(side).getAllByRole("link").map((l) => l.textContent?.trim())).toEqual([
      "Accounts",
      "Packages",
      "Healer Reg.",
      "Roles & Permissions",
      "Global Config",
      "Master Data",
      "Legal Documents",
    ]);
    expect(within(side).getAllByRole("heading").map((h) => h.textContent)).toEqual(["Access", "Content"]);
    expect(screen.queryByText("Super admin")).not.toBeInTheDocument();
    expect(within(screen.getByRole("navigation", { name: "Breadcrumb" })).getByText("Admin")).toBeInTheDocument();
  });

  it("shows only the pages the user's grants open, as the classic sidebar does", () => {
    mockUseAuth.mockReturnValue({ user: { fullName: "Ops", role: "ADMIN" } });
    mockUseMyPermissions.mockReturnValue({
      data: {
        grants: {
          CREDIT_ACCOUNTS: ["READ"],
          HEALER_REG: ["READ", "CREATE"],
          MASTER_DATA_PROTOCOLS: ["READ"],
          LEGAL_DOCUMENTS: ["READ"],
          ROLES: ["CREATE"],
        },
      },
    });
    renderAt("/admin/accounts", <AdminShell />);
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    const labels = within(side).getAllByRole("link").map((l) => l.textContent?.trim());
    expect(labels).toEqual(["Accounts", "Healer Reg.", "Master Data", "Legal Documents"]);
    expect(screen.queryByText("Admin", { selector: "div" })).not.toBeInTheDocument();
  });
});

describe("HealerShell", () => {
  it("shows the four healer pages", () => {
    mockUseUiVersion.mockReturnValue({ resolvedTheme: "dark", setTheme: vi.fn() });
    mockUseAuth.mockReturnValue({ user: { fullName: "Asha Rao", role: "HEALER" } });
    mockUseMyPermissions.mockReturnValue({ data: { grants: { PATIENT: ["READ"], PDF: ["READ"] } } });
    renderAt("/patients", <HealerShell />);
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    expect(within(side).getAllByRole("link").map((l) => l.textContent?.trim())).toEqual(["Dashboard", "Patients", "PDFs", "Profile"]);
  });

  it("hides pages the user may not read, even from the menu", () => {
    mockUseUiVersion.mockReturnValue({ resolvedTheme: "dark", setTheme: vi.fn() });
    mockUseAuth.mockReturnValue({ user: { fullName: "Asha Rao", role: "HEALER" } });
    mockUseMyPermissions.mockReturnValue({ data: { grants: { PATIENT: ["READ"], PDF: ["CREATE"] } } });
    renderAt("/patients", <HealerShell />);
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    expect(within(side).getAllByRole("link").map((l) => l.textContent?.trim())).toEqual(["Dashboard", "Patients", "Profile"]);
  });
});
