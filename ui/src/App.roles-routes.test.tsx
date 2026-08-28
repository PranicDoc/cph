import { readFileSync } from "node:fs";

import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import { Outlet, useParams, type RouteObject } from "react-router-dom";

import { renderWithDataRouter } from "./test/renderWithDataRouter";
import { adminNavItems, type AdminNavItem } from "./config/adminNavItems";

/**
 * Wave-5 join point (plan 38-10): the four role routes are registered inside
 * the existing `/admin` `AdminLayout` block (already nested under
 * `<Route element={<AdminRoute />}>`), and the SUPADM-only Roles nav entry is
 * added directly below Legal Documents.
 *
 * Two things are guarded here:
 *  1. The four role route *path patterns* resolve to their intended screen and
 *     expose `:id` as the `id` param — exercised against stub elements mounted
 *     with the exact relative path strings that App.tsx uses.
 *  2. `adminNavItems` gates the Roles entry to SUPADM only and orders it
 *     immediately after `/admin/legal-documents` (the nav-gating regression
 *     guard for T-38-10 — the nav entry is discoverability, not access control).
 *
 * A source-level assertion on App.tsx ties (1) to the real route tree without
 * mounting the whole `App` (which would pull in every provider).
 */

// --- (1) route-pattern resolution against stubs -----------------------------

function AdminLayoutStub() {
  return (
    <div>
      <p>admin-chrome</p>
      <Outlet />
    </div>
  );
}

function ScreenStub({ name }: { name: string }) {
  const { id } = useParams();
  return (
    <div>
      <p>screen:{name}</p>
      <p>id:{id ?? "none"}</p>
    </div>
  );
}

const roleRoutes: RouteObject[] = [
  {
    path: "/admin",
    element: <AdminLayoutStub />,
    children: [
      { path: "roles", element: <ScreenStub name="list" /> },
      { path: "roles/new", element: <ScreenStub name="create" /> },
      { path: "roles/:id/edit", element: <ScreenStub name="edit" /> },
    ],
  },
];

describe("role route resolution", () => {
  it("/admin/roles resolves to the list screen inside the admin chrome", () => {
    renderWithDataRouter(roleRoutes, { initialEntries: ["/admin/roles"] });
    expect(screen.getByText("admin-chrome")).toBeInTheDocument();
    expect(screen.getByText("screen:list")).toBeInTheDocument();
    expect(screen.getByText("id:none")).toBeInTheDocument();
  });

  it("/admin/roles/new resolves to the create screen", () => {
    renderWithDataRouter(roleRoutes, { initialEntries: ["/admin/roles/new"] });
    expect(screen.getByText("screen:create")).toBeInTheDocument();
  });

  it("/admin/roles/:id/edit resolves to the edit screen and exposes id", () => {
    renderWithDataRouter(roleRoutes, {
      initialEntries: ["/admin/roles/abc-123/edit"],
    });
    expect(screen.getByText("screen:edit")).toBeInTheDocument();
    expect(screen.getByText("id:abc-123")).toBeInTheDocument();
  });

});

// --- (2) nav gating + ordering --------------------------------------------

/** Mirrors AdminLayout's filter: `adminNavItems.filter(i => i.roles.includes(role))`. */
function visibleFor(role: string): AdminNavItem[] {
  return adminNavItems.filter((item) => item.roles.includes(role));
}

describe("adminNavItems — Roles entry", () => {
  it("has exactly one /admin/roles entry, SUPADM-only", () => {
    const rolesEntries = adminNavItems.filter(
      (item) => item.path === "/admin/roles"
    );
    expect(rolesEntries).toHaveLength(1);
    expect(rolesEntries[0].label).toBe("Roles & Permissions");
    expect(rolesEntries[0].roles).toEqual(["SUPADM"]);
  });

  it("sits immediately after the Legal Documents entry", () => {
    const legalIdx = adminNavItems.findIndex(
      (item) => item.path === "/admin/legal-documents"
    );
    const rolesIdx = adminNavItems.findIndex(
      (item) => item.path === "/admin/roles"
    );
    expect(legalIdx).toBeGreaterThanOrEqual(0);
    expect(rolesIdx).toBe(legalIdx + 1);
  });

  it("is excluded from an ADMIN's nav and included in a SUPADM's nav", () => {
    expect(
      visibleFor("ADMIN").some((item) => item.path === "/admin/roles")
    ).toBe(false);
    expect(
      visibleFor("SUPADM").some((item) => item.path === "/admin/roles")
    ).toBe(true);
  });
});

// --- (3) App.tsx source ties the stubs above to the real route tree --------

describe("App.tsx route registration", () => {
  const src = readFileSync("src/App.tsx", "utf8");

  it("imports the two role pages from ./pages/admin/", () => {
    expect(src).toContain('import RolesPage from "./pages/admin/RolesPage"');
    expect(src).toContain(
      'import RoleFormPage from "./pages/admin/RoleFormPage"'
    );
    expect(src).not.toContain("RoleAssignmentPage");
  });

  it("registers the three role routes with the intended elements", () => {
    expect(src).toMatch(/path="roles"\s+element=\{<RolesPage/);
    expect(src).toMatch(/path="roles\/new"\s+element=\{<RoleFormPage/);
    expect(src).toMatch(/path="roles\/:id\/edit"\s+element=\{<RoleFormPage/);
    expect(src).not.toMatch(/path="roles\/:id\/users"/);
  });

  it("places the role routes after legal-documents (inside the /admin AdminLayout block)", () => {
    expect(src.indexOf('path="roles"')).toBeGreaterThan(
      src.indexOf('path="legal-documents"')
    );
  });

  it("keeps the data-router construction untouched", () => {
    expect(src).toContain("createBrowserRouter");
    expect(src).toContain("createRoutesFromElements");
    expect(src).toContain("<RouterProvider router={router} />");
  });

  it("preserves every pre-existing route path", () => {
    for (const path of [
      'path="/login"',
      'path="/unauthorized"',
      'path="/forbidden"',
      'path="/oauth2/success"',
      'path="/legal-center"',
      'path="/admin"',
      'path="accounts"',
      'path="global-config"',
      'path="packages"',
      'path="master-data"',
      'path="healer-registration"',
      'path="legal-documents"',
      'path="*"',
    ]) {
      expect(src).toContain(path);
    }
  });
});
