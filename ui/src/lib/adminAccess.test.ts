import { describe, expect, it } from "vitest";
import { Users } from "lucide-react";
import { adminItemForPath, canSeeAdminItem, firstAdminPath, visibleAdminItems, type MyPermissions } from "./adminAccess";
import type { AdminNavItem } from "../config/adminNavItems";

const items: AdminNavItem[] = [
  { label: "Accounts", path: "/admin/accounts", icon: Users, anyOf: ["CREDIT_ACCOUNTS"] },
  { label: "Master Data", path: "/admin/master-data", icon: Users, anyOf: ["MASTER_DATA_COLORS", "MASTER_DATA_CHAKRAS", "MASTER_DATA_PROTOCOLS"] },
  { label: "Roles", path: "/admin/roles", icon: Users, anyOf: ["ROLES"] },
];

const seeder: MyPermissions = { superAdmin: false, grants: { MASTER_DATA_PROTOCOLS: ["READ", "CREATE"], PATIENT: ["READ"] } };
const supadm: MyPermissions = { superAdmin: true, grants: {} };
const healer: MyPermissions = { superAdmin: false, grants: { PATIENT: ["READ", "CREATE"] } };

describe("admin access by permission", () => {
  it("shows an item when any of its resources can be read", () => {
    expect(canSeeAdminItem(seeder, items[1])).toBe(true);
    expect(canSeeAdminItem(seeder, items[0])).toBe(false);
  });

  it("needs READ, not just some other verb", () => {
    const writeOnly: MyPermissions = { superAdmin: false, grants: { ROLES: ["CREATE"] } };
    expect(canSeeAdminItem(writeOnly, items[2])).toBe(false);
  });

  it("shows everything to the super admin and nothing without permissions", () => {
    expect(visibleAdminItems(supadm, items)).toHaveLength(3);
    expect(visibleAdminItems(healer, items)).toEqual([]);
    expect(visibleAdminItems(undefined, items)).toEqual([]);
  });

  it("lands on the first screen the user can open", () => {
    expect(firstAdminPath(seeder, items)).toBe("/admin/master-data");
    expect(firstAdminPath(supadm, items)).toBe("/admin/accounts");
    expect(firstAdminPath(healer, items)).toBeNull();
  });

  it("finds the menu item that owns a nested admin path", () => {
    expect(adminItemForPath("/admin/roles/123/edit", items)?.label).toBe("Roles");
    expect(adminItemForPath("/admin/accounts/HEALER/1/transactions", items)?.label).toBe("Accounts");
    expect(adminItemForPath("/admin", items)).toBeUndefined();
  });
});
