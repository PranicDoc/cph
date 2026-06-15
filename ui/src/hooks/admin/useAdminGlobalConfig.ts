import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api from "../../lib/axios";

export interface CreditConfigEntry {
  config_key: string;
  config_value: string;
  description: string | null;
  updated_at: string;
}

const QK = ["admin-global-config"] as const;

export function useAdminGlobalConfig() {
  return useQuery<CreditConfigEntry[]>({
    queryKey: QK,
    queryFn: () => api.get("/api/admin/credit/config").then((r) => r.data),
  });
}

export function useUpdateGlobalConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      api
        .put(`/api/admin/credit/config/${key}`, { config_value: value })
        .then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK }),
  });
}
