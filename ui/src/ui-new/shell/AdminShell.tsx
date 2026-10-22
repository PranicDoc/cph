import { useMyPermissions } from "../../hooks/useMyPermissions";
import { visibleNavItems } from "../../lib/adminAccess";
import { adminNavItems } from "../../config/adminNavItems";
import { NewShell } from "./NewShell";

const GROUP_ORDER = ["", "Access", "Content"];

/** New-view chrome for the admin app (the routes under "/admin"). Same permission filtering as classic. */
export default function AdminShell() {
  const { data: permissions } = useMyPermissions();
  const items = visibleNavItems(permissions, adminNavItems);
  return (
    <NewShell items={items} groupOrder={GROUP_ORDER} sectionLabel="Admin" />
  );
}
