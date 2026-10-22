import { LayoutDashboard, Users, FileText, User } from "lucide-react";
import type { SidebarNavItem } from "../components/Sidebar";

export const healerNavItems: SidebarNavItem[] = [
  { label: "Dashboard", path: "/",         icon: LayoutDashboard, exact: true },
  { label: "Patients",  path: "/patients", icon: Users,    anyOf: ["PATIENT"] },
  { label: "PDFs",      path: "/pdf-list", icon: FileText, anyOf: ["PDF"] },
  { label: "Profile",   path: "/profile",  icon: User },
];
