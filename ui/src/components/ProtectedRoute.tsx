import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { Activity } from "lucide-react";
import { useOutstandingConsents } from "../hooks/legal/useOutstandingConsents";

/**
 * Single enforcement point for REG-01/REG-02/REG-03 (D-03).
 *
 * This file deliberately adds no backend logic: `ConsentService
 * .outstandingConsents()` already computes the correct per-practitioner
 * outstanding-consent set for both a brand-new practitioner (REG-01) and a
 * returning practitioner after a republish (REG-03), so both cases funnel
 * through the identical check below (D-02/D-06). `Healer.active` is
 * deliberately not consulted here either — it keeps its separate
 * admin-deactivation meaning, checked in `AuthService`.
 */

const GATE_EXEMPT_ROLES = ["ADMIN", "SUPADM"];
export const LEGAL_CENTER_PATH = "/legal-center";

function LoadingScreen() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="flex flex-col items-center gap-4">
        <Activity className="w-10 h-10 text-primary animate-pulse" />
        <p className="text-text-light font-medium">Loading...</p>
      </div>
    </div>
  );
}

export default function ProtectedRoute() {
  const { isAuthenticated, isLoading, user } = useAuth();
  const location = useLocation();
  // D-01: exemption requires an explicitly recognised admin role. A missing
  // or unrecognised role is NOT exempt, so the gate fails closed. Admins
  // reach /admin/legal-documents through AdminRoute, which this file does
  // not touch, so a fail-closed default cannot lock an admin out of the
  // screen that publishes policies.
  const isExempt = !!user?.role && GATE_EXEMPT_ROLES.includes(user.role);
  // The enabled argument suppresses the request entirely for exempt
  // accounts and before auth resolves.
  const outstanding = useOutstandingConsents(isAuthenticated && !isExempt);

  if (isLoading) return <LoadingScreen />;

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!isExempt) {
    // Returning the spinner rather than <Outlet /> is load-bearing: it
    // guarantees no protected page mounts (and therefore fires none of its
    // own data queries) before the gate has answered (T-34-07).
    if (outstanding.isLoading) return <LoadingScreen />;

    // An errored check counts as gated, never as cleared (fail-closed,
    // T-34-12).
    const gateActive = outstanding.isError || (outstanding.data?.length ?? 0) > 0;

    // replace is deliberate so the blocked URL never enters history and the
    // Back button cannot re-enter it (T-34-02).
    if (gateActive && location.pathname !== LEGAL_CENTER_PATH) {
      return <Navigate to={LEGAL_CENTER_PATH} replace />;
    }

    // D-05: once the last document is accepted the outstanding query is
    // invalidated, comes back empty, and this branch sends the
    // practitioner straight to the dashboard with no confirmation screen.
    if (!gateActive && location.pathname === LEGAL_CENTER_PATH) {
      return <Navigate to="/" replace />;
    }
  }

  return <Outlet />;
}
