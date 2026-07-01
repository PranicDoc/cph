import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

export interface HealerItem {
  id: string;
  fullName: string;
  email: string;
  phone?: string;
  role: string;
  healingLevel?: string;
  active: boolean;
  clinicId: string;
  clinicCode: string;
  createdAt: string;
}

/** Healers in the current admin's clinic (ADMIN + SUPADM) */
export function useClinicHealers() {
  return useQuery<HealerItem[]>({
    queryKey: ["clinic-healers"],
    queryFn: () => api.get("/api/v1/healers").then((r) => r.data),
  });
}

/** Healers for a specific clinic (SUPADM only) */
export function useClinicHealersByClinicId(clinicId: string | null) {
  return useQuery<HealerItem[]>({
    queryKey: ["clinic-healers", clinicId],
    queryFn: () => api.get(`/api/v1/healers/clinic/${clinicId}`).then((r) => r.data),
    enabled: !!clinicId,
  });
}

export function useToggleHealerActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api.patch<HealerItem>(`/api/v1/healers/${id}/active`, null, { params: { active } }).then((r) => r.data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["clinic-healers"] });
      toast.success(`Healer ${data.active ? "activated" : "deactivated"}`);
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to update healer status", { duration: 5000 }),
  });
}
