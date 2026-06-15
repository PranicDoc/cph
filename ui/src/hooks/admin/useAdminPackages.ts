import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import api from "../../lib/axios";

export interface AdminPackage {
  id: string;
  name: string;
  credits: number;
  price_paise: number;
  currency: string;
  active: boolean;
  display_order: number;
  created_at: string;
  updated_at: string;
}

export interface CreatePackagePayload {
  name: string;
  credits: number;
  price_paise: number;
  currency?: string;
  display_order?: number;
}

export interface UpdatePackagePayload {
  name?: string;
  credits?: number;
  price_paise?: number;
  currency?: string;
  active?: boolean;
  display_order?: number;
}

const QK = ["admin-packages"] as const;

export function useAdminPackages() {
  return useQuery<AdminPackage[]>({
    queryKey: QK,
    queryFn: () => api.get("/api/admin/credit/packages").then((r) => r.data),
  });
}

export function useCreatePackage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreatePackagePayload) =>
      api.post("/api/admin/credit/packages", payload).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK }),
  });
}

export function useUpdatePackage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdatePackagePayload }) =>
      api.put(`/api/admin/credit/packages/${id}`, payload).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK }),
  });
}

export function useDeactivatePackage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/admin/credit/packages/${id}`).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK }),
  });
}
