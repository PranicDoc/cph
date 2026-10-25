import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { useAuth } from "../context/AuthContext";
import { hasPermission, type MyPermissions } from "../lib/adminAccess";

/**
 * The signed-in user's own permissions (GET /api/v1/auth/me/permissions), used to decide
 * which admin screens to show. The server still checks every request.
 */
export function useMyPermissions() {
  const { user, isAuthenticated } = useAuth();
  return useQuery<MyPermissions>({
    queryKey: ["my-permissions", user?.id],
    queryFn: () => api.get("/api/v1/auth/me/permissions").then((r) => r.data),
    enabled: isAuthenticated && !!user?.id,
    staleTime: 60_000,
  });
}

/**
 * can(resource, verb) for showing or hiding action buttons. Answers false while the
 * permissions are loading, so a button never flashes up and then disappears.
 */
export function useCan() {
  const { data } = useMyPermissions();
  return (resource: string, verb: string) => hasPermission(data, resource, verb);
}
