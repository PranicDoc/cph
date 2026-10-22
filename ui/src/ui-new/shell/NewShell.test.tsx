import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { Database, LayoutDashboard, Package, ShieldCheck, Users } from "lucide-react";

const { mockUseUiVersion, mockUseAuth } = vi.hoisted(() => ({
  mockUseUiVersion: vi.fn(),
  mockUseAuth: vi.fn(),
}));

vi.mock("../../ui-version", () => ({ useUiVersion: () => mockUseUiVersion() }));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => mockUseAuth() }));
vi.mock("../../components/UserProfile", () => ({ default: () => <button type="button">profile menu</button> }));

import { NewShell, type ShellNavItem } from "./NewShell";

const setTheme = vi.fn();

const healerItems: ShellNavItem[] = [
  { label: "Dashboard", path: "/", icon: LayoutDashboard, exact: true },
  { label: "Patients", path: "/patients", icon: Users },
];

const adminItems: ShellNavItem[] = [
  { label: "Accounts", path: "/admin/accounts", icon: Users },
  { label: "Packages", path: "/admin/packages", icon: Package },
  { label: "Roles & Permissions", path: "/admin/roles", icon: ShieldCheck, group: "Access" },
  { label: "Master Data", path: "/admin/master-data", icon: Database, group: "Content" },
  { label: "Legal Documents", path: "/admin/legal-documents", icon: Database, group: "Content" },
  { label: "Global Config", path: "/admin/global-config", icon: Database, group: "Content" },
];

function renderShell(path: string, props: Partial<React.ComponentProps<typeof NewShell>> = {}) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          element={<NewShell items={healerItems} {...props} />}
        >
          <Route path="*" element={<div>PAGE CONTENT</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe("NewShell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseUiVersion.mockReturnValue({ resolvedTheme: "dark", setTheme });
    mockUseAuth.mockReturnValue({ user: { fullName: "Asha Rao", role: "HEALER" } });
  });

  it("renders the page inside the shell, with the brand", () => {
    renderShell("/patients");
    expect(screen.getByText("PAGE CONTENT")).toBeInTheDocument();
    expect(screen.getAllByRole("img", { name: "HealingARK" }).length).toBeGreaterThan(0);
  });

  it("lists every nav item as a link in the sidebar and marks the current page", () => {
    renderShell("/patients");
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    expect(within(side).getByRole("link", { name: /dashboard/i })).toHaveAttribute("href", "/");
    expect(within(side).getByRole("link", { name: /patients/i })).toHaveAttribute("aria-current", "page");
    expect(within(side).getByRole("link", { name: /dashboard/i })).not.toHaveAttribute("aria-current");
  });

  it("treats sub-pages as the current section, but the root only on an exact match", () => {
    renderShell("/patients/abc/edit");
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    expect(within(side).getByRole("link", { name: /patients/i })).toHaveAttribute("aria-current", "page");
    expect(within(side).getByRole("link", { name: /dashboard/i })).not.toHaveAttribute("aria-current");
  });

  it("shows the signed-in user's name, and no role, at the bottom of the sidebar", () => {
    renderShell("/");
    expect(screen.getByText("Asha Rao")).toBeInTheDocument();
    expect(screen.queryByText("Healer")).not.toBeInTheDocument();
  });

  it("shows a section crumb before the page name when asked to", () => {
    renderShell("/admin/accounts", { items: adminItems, sectionLabel: "Admin" });
    const crumbs = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(crumbs).getByText("Admin")).toBeInTheDocument();
    expect(within(crumbs).getByText("Accounts")).toBeInTheDocument();
  });

  it("groups nav items under headings in the requested order", () => {
    renderShell("/admin/accounts", {
      items: adminItems,
      groupOrder: ["", "Access", "Content"],
    });
    const side = screen.getByRole("navigation", { name: "Sidebar" });
    const headings = within(side).getAllByRole("heading").map((h) => h.textContent);
    expect(headings).toEqual(["Access", "Content"]);
    const order = within(side).getAllByRole("link").map((l) => l.textContent?.trim());
    expect(order).toEqual(["Accounts", "Packages", "Roles & Permissions", "Master Data", "Legal Documents", "Global Config"]);
  });

  it("switches between dark and light from the theme button", async () => {
    renderShell("/");
    await userEvent.click(screen.getAllByRole("button", { name: /switch theme/i })[0]);
    expect(setTheme).toHaveBeenCalledWith("light");
  });

  it("puts all items in the mobile tab bar when there are five or fewer", () => {
    renderShell("/");
    const tabs = screen.getByRole("navigation", { name: "Main" });
    expect(within(tabs).getAllByRole("link")).toHaveLength(2);
    expect(within(tabs).queryByRole("button", { name: /more/i })).not.toBeInTheDocument();
  });

  it("keeps four tabs and a More button when there are more than five items", async () => {
    renderShell("/admin/accounts", { items: adminItems });
    const tabs = screen.getByRole("navigation", { name: "Main" });
    expect(within(tabs).getAllByRole("link")).toHaveLength(4);
    await userEvent.click(within(tabs).getByRole("button", { name: /more/i }));
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).getByRole("link", { name: /legal documents/i })).toBeInTheDocument();
    expect(within(sheet).getByRole("link", { name: /global config/i })).toBeInTheDocument();
  });
});
