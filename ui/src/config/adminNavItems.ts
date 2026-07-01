import { Users, Settings, Package, Sliders, Database, Building2, UserPlus } from "lucide-react";
import type { SidebarNavItem } from "../components/Sidebar";

export interface AdminNavItem extends SidebarNavItem {
  roles: string[];
}

export const adminNavItems: AdminNavItem[] = [
  { label: "Accounts",      path: "/admin/accounts",      icon: Users,    roles: ["ADMIN", "SUPADM"] },
  { label: "Clinic Config", path: "/admin/clinic-config", icon: Settings, roles: ["ADMIN", "SUPADM"] },
  { label: "Global Config", path: "/admin/global-config", icon: Sliders,  roles: ["SUPADM"] },
  { label: "Packages",      path: "/admin/packages",      icon: Package,  roles: ["SUPADM"] },
  { label: "Master Data",   path: "/admin/master-data",         icon: Database,   roles: ["ADMIN", "SUPADM"] },
  { label: "Clinics",      path: "/admin/clinics",             icon: Building2,  roles: ["SUPADM"] },
  { label: "Healer Reg.",  path: "/admin/healer-registration", icon: UserPlus,   roles: ["ADMIN", "SUPADM"] },
];
