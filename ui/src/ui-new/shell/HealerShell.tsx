import { healerNavItems } from "../../config/healerNavItems";
import { useMyPermissions } from "../../hooks/useMyPermissions";
import { visibleNavItems } from "../../lib/adminAccess";
import { NewShell } from "./NewShell";

/** New-view chrome for the healer app (the routes under "/"); menu items follow the user's permissions. */
export default function HealerShell() {
  const { data: permissions } = useMyPermissions();
  return <NewShell items={visibleNavItems(permissions, healerNavItems)} />;
}
