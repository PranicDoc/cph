import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { useAuth } from "../context/AuthContext";
import type { MyPermissions } from "../lib/adminAccess";

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
