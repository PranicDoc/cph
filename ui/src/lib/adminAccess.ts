import type { AdminNavItem } from "../config/adminNavItems";

/** GET /api/v1/auth/me/permissions: the super admin flag, or the user's grants across all their roles. */
export interface MyPermissions {
  superAdmin: boolean;
  grants: Record<string, string[]>;
}

/** An admin screen is shown when the user may READ any of the resources it lists. */
export function canSeeAdminItem(perms: MyPermissions | undefined, item: AdminNavItem): boolean {
  if (!perms) return false;
  if (perms.superAdmin) return true;
  return item.anyOf.some((resource) => perms.grants[resource]?.includes("READ"));
}

export function visibleAdminItems(perms: MyPermissions | undefined, items: AdminNavItem[]): AdminNavItem[] {
  return items.filter((item) => canSeeAdminItem(perms, item));
}

/** Where "/admin" takes the user: the first screen they can open, or null when there is none. */
export function firstAdminPath(perms: MyPermissions | undefined, items: AdminNavItem[]): string | null {
  return visibleAdminItems(perms, items)[0]?.path ?? null;
}

/** The menu item a path belongs to, including nested pages (e.g. a role's edit page under Roles). */
export function adminItemForPath(pathname: string, items: AdminNavItem[]): AdminNavItem | undefined {
  return items.find((item) => pathname === item.path || pathname.startsWith(item.path + "/"));
}
