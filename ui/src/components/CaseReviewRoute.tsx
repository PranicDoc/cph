import { Navigate, Outlet } from "react-router-dom";
import { useMyPermissions } from "../hooks/useMyPermissions";

/**
 * Layout route for the review screens. Renders nothing while the permissions load (so a deep link
 * neither flashes nor bounces early), the screen when case review is on, and sends everyone home
 * when it is off.
 */
export function CaseReviewRoute() {
  const { data, isPending } = useMyPermissions();
  if (isPending) return null;
  if (data?.caseReviewEnabled !== true) return <Navigate to="/" replace />;
  return <Outlet />;
}
