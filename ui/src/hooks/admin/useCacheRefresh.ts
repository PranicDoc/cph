import { useMutation, useQuery } from "@tanstack/react-query";
import api from "../../lib/axios";

export interface CacheRefreshResult {
  caches: string[];
  key: string | null;
  redisCleared: boolean;
}

/** Super admin only: the names of the server's caches. */
export function useCacheNames() {
  return useQuery<string[]>({
    queryKey: ["admin-cache-names"],
    queryFn: () => api.get("/api/v1/admin/cache").then((r) => r.data),
  });
}

/** No `cacheName` = every cache; no `key` = every entry. */
export function useRefreshCache() {
  return useMutation({
    mutationFn: (params: { cacheName?: string; key?: string }) =>
      api
        .post<CacheRefreshResult>("/api/v1/admin/cache/refresh", null, { params })
        .then((r) => r.data),
  });
}
