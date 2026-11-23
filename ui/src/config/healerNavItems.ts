import { LayoutDashboard, Users, FileText, User, ClipboardCheck, Send } from "lucide-react";
import type { SidebarNavItem } from "../components/Sidebar";

export const healerNavItems: SidebarNavItem[] = [
  { label: "Dashboard", path: "/",         icon: LayoutDashboard, exact: true },
  { label: "Patients",  path: "/patients", icon: Users,    anyOf: ["PATIENT"] },
  { label: "PDFs",      path: "/pdf-list", icon: FileText, anyOf: ["PDF"] },
  { label: "Reviews",   path: "/reviews",  icon: ClipboardCheck, requires: { resource: "CASE", operation: "REVIEW" }, requiresFeature: "caseReview", unreadDot: true },
  { label: "Submissions", path: "/submissions", icon: Send, anyOf: ["CASE"], verb: "UPDATE", requiresFeature: "caseReview", unreadDot: true },
  { label: "Profile",   path: "/profile",  icon: User },
];
