import { describe, expect, it } from "vitest";
import { Users } from "lucide-react";
import { adminItemForPath, canSeeNavItem, firstAdminPath, visibleNavItems, type MyPermissions } from "./adminAccess";
import type { AdminNavItem } from "../config/adminNavItems";
import type { SidebarNavItem } from "../components/Sidebar";

const items: AdminNavItem[] = [
  { label: "Accounts", path: "/admin/accounts", icon: Users, anyOf: ["CREDIT_ACCOUNTS"] },
  { label: "Master Data", path: "/admin/master-data", icon: Users, anyOf: ["MASTER_DATA_COLORS", "MASTER_DATA_CHAKRAS", "MASTER_DATA_PROTOCOLS"] },
  { label: "Roles", path: "/admin/roles", icon: Users, anyOf: ["ROLES"] },
];

const seeder: MyPermissions = { grants: { MASTER_DATA_PROTOCOLS: ["READ", "CREATE"], PATIENT: ["READ"] } };
const everything: MyPermissions = { grants: { CREDIT_ACCOUNTS: ["READ"], MASTER_DATA_COLORS: ["READ"], ROLES: ["READ", "ADMIN"] } };
const healer: MyPermissions = { grants: { PATIENT: ["READ", "CREATE"] } };

describe("navigation by permission", () => {
  it("shows an item when any of its resources can be read", () => {
    expect(canSeeNavItem(seeder, items[1])).toBe(true);
    expect(canSeeNavItem(seeder, items[0])).toBe(false);
  });

  it("needs READ, not just some other verb", () => {
    expect(canSeeNavItem({ grants: { ROLES: ["CREATE"] } }, items[2])).toBe(false);
  });

  it("always shows items that need no permission, and hides gated ones until permissions load", () => {
    const dashboard: SidebarNavItem = { label: "Dashboard", path: "/", icon: Users };
    const patients: SidebarNavItem = { label: "Patients", path: "/patients", icon: Users, anyOf: ["PATIENT"] };
    expect(visibleNavItems(undefined, [dashboard, patients])).toEqual([dashboard]);
    expect(visibleNavItems(healer, [dashboard, patients])).toEqual([dashboard, patients]);
    expect(visibleNavItems({ grants: {} }, [dashboard, patients])).toEqual([dashboard]);
  });

  it("lands on the first admin screen the user can open", () => {
    expect(firstAdminPath(seeder, items)).toBe("/admin/master-data");
    expect(firstAdminPath(everything, items)).toBe("/admin/accounts");
    expect(firstAdminPath(healer, items)).toBeNull();
  });

  it("finds the menu item that owns a nested admin path", () => {
    expect(adminItemForPath("/admin/roles/123/edit", items)?.label).toBe("Roles");
    expect(adminItemForPath("/admin/accounts/HEALER/1/transactions", items)?.label).toBe("Accounts");
    expect(adminItemForPath("/admin", items)).toBeUndefined();
  });
});
