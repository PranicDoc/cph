import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

export interface AdminHealer {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  role: string;
  healingLevel: string | null;
  active: boolean;
  createdAt: string;
  hasPassword: boolean;
  hasGoogleLinked: boolean;
}

const QK = ["admin-healers"] as const;

// GET /api/v1/healers is SUPADM-only server-side — pass enabled=false for non-SUPADM
// callers so the query never fires (and never 403s) for them.
export function useAdminHealers(enabled = true) {
  return useQuery<AdminHealer[]>({
    queryKey: QK,
    queryFn: () => api.get("/api/v1/healers").then((r) => r.data),
    enabled,
  });
}

export function useSetHealerActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api
        .patch<AdminHealer>(`/api/v1/healers/${id}/active`, null, { params: { active } })
        .then((r) => r.data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success(`${data.fullName} ${data.active ? "activated" : "deactivated"}`);
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to update healer status", { duration: 5000 }),
  });
}

export function useRemoveHealer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/v1/healers/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success("Healer removed");
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to remove healer", { duration: 5000 }),
  });
}
