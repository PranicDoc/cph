import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";

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
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useUpdatePackage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdatePackagePayload }) =>
      api.put(`/api/admin/credit/packages/${id}`, payload).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success("Saved");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to save", { duration: 5000 }),
  });
}

export function useDeactivatePackage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/admin/credit/packages/${id}`).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK });
      toast.success("Deleted");
    },
    onError: (e) => toast.error(extractDetail(e) || "Failed to delete", { duration: 5000 }),
  });
}
