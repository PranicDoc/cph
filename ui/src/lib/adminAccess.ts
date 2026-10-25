import type { AdminNavItem } from "../config/adminNavItems";
import type { SidebarNavItem } from "../components/Sidebar";

/** GET /api/v1/auth/me/permissions: resource name to the verbs the user holds across all their roles. */
export interface MyPermissions {
  grants: Record<string, string[]>;
}

/** Whether the user holds this verb on this resource. False until the permissions have loaded. */
export function hasPermission(perms: MyPermissions | undefined, resource: string, verb: string): boolean {
  return !!perms?.grants[resource]?.includes(verb);
}

/**
 * A menu item is shown when it needs no permission, or when the user holds its verb (READ unless
 * the item names another) on any of the resources it lists. Gated items stay hidden until the
 * permissions have loaded.
 */
export function canSeeNavItem(perms: MyPermissions | undefined, item: SidebarNavItem): boolean {
  if (!item.anyOf?.length) return true;
  if (!perms) return false;
  const verb = item.verb ?? "READ";
  return item.anyOf.some((resource) => perms.grants[resource]?.includes(verb));
}

export function visibleNavItems<T extends SidebarNavItem>(perms: MyPermissions | undefined, items: T[]): T[] {
  return items.filter((item) => canSeeNavItem(perms, item));
}

/** Where "/admin" takes the user: the first admin screen they can open, or null when there is none. */
export function firstAdminPath(perms: MyPermissions | undefined, items: AdminNavItem[]): string | null {
  return visibleNavItems(perms, items)[0]?.path ?? null;
}

/** The menu item a path belongs to, including nested pages (e.g. a role's edit page under Roles). */
export function adminItemForPath(pathname: string, items: AdminNavItem[]): AdminNavItem | undefined {
  return items.find((item) => pathname === item.path || pathname.startsWith(item.path + "/"));
}
