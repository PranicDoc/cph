import { Outlet } from "react-router-dom";
import { BrandMark } from "../components/BrandMark";

/** New-view chrome for pages opened without signing in (feedback, health form, sign-up, invites). */
export default function NewPublicShell() {
  return (
    <div className="hl-public">
      <header className="hl-pubhead hl-glass">
        <BrandMark />
        <span className="font-semibold tracking-tight">HealingARK</span>
      </header>
      <main className="hl-pubmain">
        <div className="hl-canvas">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
