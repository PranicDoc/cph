import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

const { mockUseUiVersion, mockUseAuth } = vi.hoisted(() => ({
  mockUseUiVersion: vi.fn(),
  mockUseAuth: vi.fn(),
}));

vi.mock("../../ui-version", () => ({ useUiVersion: () => mockUseUiVersion() }));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => mockUseAuth() }));
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
    expect(screen.getByText("Super admin")).toBeInTheDocument();
    expect(within(screen.getByRole("navigation", { name: "Breadcrumb" })).getByText("Admin")).toBeInTheDocument();
  });

  it("hides the super-admin-only pages from a plain admin, as the classic sidebar does", () => {
    mockUseAuth.mockReturnValue({ user: { fullName: "Ops", role: "ADMIN" } });
    renderAt("/admin/accounts", <AdminShell />);
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    const labels = within(side).getAllByRole("link").map((l) => l.textContent?.trim());
    expect(labels).toEqual(["Accounts", "Healer Reg.", "Master Data", "Legal Documents"]);
    expect(screen.getByText("Admin", { selector: "div" })).toBeInTheDocument();
  });
});

describe("HealerShell", () => {
  it("shows the four healer pages", () => {
    mockUseUiVersion.mockReturnValue({ resolvedTheme: "dark", setTheme: vi.fn() });
    mockUseAuth.mockReturnValue({ user: { fullName: "Asha Rao", role: "HEALER" } });
    renderAt("/patients", <HealerShell />);
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    expect(within(side).getAllByRole("link").map((l) => l.textContent?.trim())).toEqual(["Dashboard", "Patients", "PDFs", "Profile"]);
  });
});
