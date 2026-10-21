import type { ReactNode } from "react";
import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuth } from "../../context/AuthContext";
import { BrandMark } from "../components/BrandMark";

/** New-view version of the Legal Center chrome: brand plus the one escape hatch, Logout. */
export default function NewLegalCenterShell({ children }: { children: ReactNode }) {
  const { logout } = useAuth();
  return (
    <div className="hl-public">
      <header className="hl-pubhead hl-glass">
        <BrandMark />
        <span className="font-semibold tracking-tight">HealingARK</span>
        <Button
          type="button"
          variant="ghost"
          onClick={() => logout()}
          className="ml-auto gap-2"
          data-testid="legal-center-logout"
        >
          <LogOut className="size-4" />
          Logout
        </Button>
      </header>
      <main className="hl-pubmain" style={{ maxWidth: "56rem" }}>
        <div className="hl-canvas">{children}</div>
      </main>
    </div>
  );
}
