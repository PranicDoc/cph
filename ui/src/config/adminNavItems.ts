import { Users, Package, Sliders, Database, UserPlus, FileText, ShieldCheck } from "lucide-react";
import type { SidebarNavItem } from "../components/Sidebar";

export interface AdminNavItem extends SidebarNavItem {
  roles: string[];
  /** Sidebar heading in the new view; the classic sidebar ignores it. */
  group?: string;
}

export const adminNavItems: AdminNavItem[] = [
  { label: "Accounts",      path: "/admin/accounts",      icon: Users,    roles: ["ADMIN", "SUPADM"] },
  { label: "Global Config", path: "/admin/global-config", icon: Sliders,  roles: ["SUPADM"], group: "Content" },
  { label: "Packages",      path: "/admin/packages",      icon: Package,  roles: ["SUPADM"] },
  { label: "Master Data",   path: "/admin/master-data",         icon: Database,   roles: ["ADMIN", "SUPADM"], group: "Content" },
  { label: "Healer Reg.",  path: "/admin/healer-registration", icon: UserPlus,   roles: ["ADMIN", "SUPADM"], group: "Access" },
  { label: "Legal Documents", path: "/admin/legal-documents", icon: FileText, roles: ["ADMIN", "SUPADM"], group: "Content" },
  { label: "Roles & Permissions", path: "/admin/roles", icon: ShieldCheck, roles: ["SUPADM"], group: "Access" },
];
