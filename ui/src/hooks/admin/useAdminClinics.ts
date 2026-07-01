import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

export interface ClinicItem {
  id: string;
  code: string;
  name: string;
  phone?: string;
  email?: string;
  city?: string;
  state?: string;
  active: boolean;
  createdAt: string;
}

export interface ClinicCreatePayload {
  code: string;
  name: string;
  phone?: string;
  email?: string;
}

const QK = ["admin-clinics"] as const;

export function useAdminClinics() {
  return useQuery<ClinicItem[]>({
    queryKey: QK,
    queryFn: () => api.get("/api/v1/clinics").then((r) => r.data),
  });
}

export function useCreateClinic() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ClinicCreatePayload) =>
      api.post<ClinicItem>("/api/v1/clinics", payload).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success("Clinic created");
    },
    onError: (e) =>
      toast.error(extractDetail(e) || "Failed to create clinic", { duration: 5000 }),
  });
}
