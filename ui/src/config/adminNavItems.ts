import { Users, Package, Sliders, Database, UserPlus, FileText, ShieldCheck } from "lucide-react";
import type { SidebarNavItem } from "../components/Sidebar";

export interface AdminNavItem extends SidebarNavItem {
  /** Shown to users who may READ any of these RBAC resources (the super admin sees everything). */
  anyOf: string[];
  /** Sidebar heading in the new view; the classic sidebar ignores it. */
  group?: string;
}

// Order matters: "/admin" opens the first item the user can see.
export const adminNavItems: AdminNavItem[] = [
  { label: "Accounts",      path: "/admin/accounts",      icon: Users,    anyOf: ["CREDIT_ACCOUNTS"] },
  { label: "Global Config", path: "/admin/global-config", icon: Sliders,  anyOf: ["GLOBAL_CONFIG"], group: "Content" },
  { label: "Packages",      path: "/admin/packages",      icon: Package,  anyOf: ["PACKAGES"] },
  { label: "Master Data",   path: "/admin/master-data",         icon: Database,   anyOf: ["MASTER_DATA_COLORS", "MASTER_DATA_CHAKRAS", "MASTER_DATA_PROTOCOLS"], group: "Content" },
  { label: "Healer Reg.",  path: "/admin/healer-registration", icon: UserPlus,   anyOf: ["HEALER_REG"], group: "Access" },
  { label: "Legal Documents", path: "/admin/legal-documents", icon: FileText, anyOf: ["LEGAL_DOCUMENTS"], group: "Content" },
  { label: "Roles & Permissions", path: "/admin/roles", icon: ShieldCheck, anyOf: ["ROLES"], group: "Access" },
];
