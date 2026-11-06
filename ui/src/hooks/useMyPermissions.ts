import { useQuery } from "@tanstack/react-query";
import { isAxiosError } from "axios";
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
    // Without permissions the gated tabs stay hidden, so don't give up on one failed request.
    // A 401/403 is a real answer: the axios client has already tried refreshing.
    retry: (failures, error) => {
      const status = isAxiosError(error) ? error.response?.status : undefined;
      return failures < 3 && status !== 401 && status !== 403;
    },
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
