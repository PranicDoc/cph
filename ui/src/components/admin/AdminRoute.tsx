import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { Activity } from "lucide-react";
import { useMyPermissions } from "../../hooks/useMyPermissions";
import { adminNavItems } from "../../config/adminNavItems";
import { adminItemForPath, canSeeAdminItem, firstAdminPath } from "../../lib/adminAccess";

function Loading() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="flex flex-col items-center gap-4">
        <Activity className="w-10 h-10 text-primary animate-pulse" />
        <p className="text-text-light font-medium">Loading...</p>
      </div>
    </div>
  );
}

/**
 * Gate for "/admin": opened by RBAC permissions, not by the user's role. Each screen needs
 * READ on one of its resources (see adminNavItems); "/admin" itself opens the first screen
 * the user can use. The server checks every request on its own.
 */
export default function AdminRoute() {
  const { isAuthenticated, isLoading } = useAuth();
  const { data: permissions, isLoading: permissionsLoading, isError } = useMyPermissions();
  const { pathname } = useLocation();

  if (isLoading) return <Loading />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (permissionsLoading) return <Loading />;
  if (isError) return <Navigate to="/forbidden" replace />;

  const first = firstAdminPath(permissions, adminNavItems);
  if (!first) return <Navigate to="/forbidden" replace />;

  const item = adminItemForPath(pathname, adminNavItems);
  if (!item) return pathname === "/admin" || pathname === "/admin/" ? <Navigate to={first} replace /> : <Outlet />;
  if (!canSeeAdminItem(permissions, item)) return <Navigate to="/forbidden" replace />;

  return <Outlet />;
}
