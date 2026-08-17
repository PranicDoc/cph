import { ReactNode } from "react";
import { Activity, LogOut } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useAuth } from "../../context/AuthContext";

/**
 * D-04: while a practitioner has outstanding mandatory consent, their
 * account has a live session but is not yet "activated" in the product
 * sense — the full `Layout` nav must not render, since nothing behind it
 * is reachable anyway. This shell exists to give that state its own
 * minimal, branded chrome: `PublicShell`'s exact header markup plus a
 * Logout control. Logout is deliberately present here because D-04 makes
 * it the only escape hatch from this full-screen takeover.
 *
 * A children wrapper (not a route-level layout element rendering a nested
 * route) because this phase mounts exactly one page inside it, and that
 * page needs to render its own full-screen error state outside the shell
 * (LegalCenter.tsx's error branch does not nest inside this component).
 */
export default function LegalCenterShell({ children }: { children: ReactNode }) {
  const { logout } = useAuth();

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-gradient-to-r from-primary-dark to-primary text-white shadow-lg shadow-primary/20">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-white/20 rounded-xl flex items-center justify-center">
              <Activity className="w-6 h-6 text-white" />
            </div>
            <span className="text-2xl font-bold">HealingDoc</span>
          </div>
          <Button
            type="button"
            variant="ghost"
            onClick={() => logout()}
            className="gap-2 text-white hover:bg-white/10 hover:text-white"
            data-testid="legal-center-logout"
          >
            <LogOut className="w-4 h-4" />
            Logout
          </Button>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto p-6">{children}</main>
    </div>
  );
}
