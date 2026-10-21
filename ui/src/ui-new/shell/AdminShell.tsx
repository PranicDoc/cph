import { useAuth } from "../../context/AuthContext";
import { adminNavItems } from "../../config/adminNavItems";
import { NewShell } from "./NewShell";

const GROUP_ORDER = ["", "Access", "Content"];

/** New-view chrome for the admin app (the routes under "/admin"). Same role filtering as classic. */
export default function AdminShell() {
  const { user } = useAuth();
  const items = adminNavItems.filter((item) => item.roles.includes(user?.role ?? ""));
  return (
    <NewShell
      items={items}
      groupOrder={GROUP_ORDER}
      sectionLabel="Admin"
      roleLabel={user?.role === "SUPADM" ? "Super admin" : "Admin"}
    />
  );
}
