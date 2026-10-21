import { healerNavItems } from "../../config/healerNavItems";
import { NewShell } from "./NewShell";

/** New-view chrome for the healer app (the routes under "/"). */
export default function HealerShell() {
  return <NewShell items={healerNavItems} />;
}
