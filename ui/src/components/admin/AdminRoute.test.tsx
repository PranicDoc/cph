import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

const { mockUseAuth, mockUseMyPermissions } = vi.hoisted(() => ({
  mockUseAuth: vi.fn(),
  mockUseMyPermissions: vi.fn(),
}));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => mockUseAuth() }));
vi.mock("../../hooks/useMyPermissions", () => ({ useMyPermissions: () => mockUseMyPermissions() }));

import AdminRoute from "./AdminRoute";

function at(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/forbidden" element={<p>forbidden</p>} />
        <Route path="/login" element={<p>login</p>} />
        <Route element={<AdminRoute />}>
          <Route path="/admin" element={<p>admin index</p>} />
          <Route path="/admin/accounts" element={<p>accounts</p>} />
          <Route path="/admin/master-data" element={<p>master data</p>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

const seeder = { superAdmin: false, grants: { MASTER_DATA_PROTOCOLS: ["READ", "CREATE"] } };

describe("AdminRoute, opened by permissions", () => {
  beforeEach(() => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, user: { id: "1", role: "HEALER" } });
  });

  it("lets a healer with master data grants into master data", () => {
    mockUseMyPermissions.mockReturnValue({ data: seeder, isLoading: false, isError: false });
    at("/admin/master-data");
    expect(screen.getByText("master data")).toBeInTheDocument();
  });

  it("refuses a screen the grants do not cover", () => {
    mockUseMyPermissions.mockReturnValue({ data: seeder, isLoading: false, isError: false });
    at("/admin/accounts");
    expect(screen.getByText("forbidden")).toBeInTheDocument();
  });

  it("opens the first allowed screen from /admin", () => {
    mockUseMyPermissions.mockReturnValue({ data: seeder, isLoading: false, isError: false });
    at("/admin");
    expect(screen.getByText("master data")).toBeInTheDocument();
  });

  it("refuses the admin area to a user with no admin permission", () => {
    mockUseMyPermissions.mockReturnValue({ data: { superAdmin: false, grants: { PATIENT: ["READ"] } }, isLoading: false, isError: false });
    at("/admin/master-data");
    expect(screen.getByText("forbidden")).toBeInTheDocument();
  });

  it("lets the super admin in everywhere", () => {
    mockUseMyPermissions.mockReturnValue({ data: { superAdmin: true, grants: {} }, isLoading: false, isError: false });
    at("/admin/accounts");
    expect(screen.getByText("accounts")).toBeInTheDocument();
  });

  it("sends a signed-out visitor to login", () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, user: null });
    mockUseMyPermissions.mockReturnValue({ data: undefined, isLoading: false, isError: false });
    at("/admin/accounts");
    expect(screen.getByText("login")).toBeInTheDocument();
  });
});
