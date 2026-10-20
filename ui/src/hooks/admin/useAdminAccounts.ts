import { useQuery } from "@tanstack/react-query";
import api from "../../lib/axios";

export interface AdminAccount {
  id: string;
  owner_type: "HEALER" | "CLINIC";
  owner_id: string;
  owner_name: string | null;
  free_balance: number;
  paid_balance: number;
  plan_tier: string;
  plan_expires_at: string | null;
  payg_enabled: boolean;
  payg_bundle_key: string | null;
  created_at: string;
  updated_at: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export function useAdminAccounts(ownerType?: string, page = 0, size = 20) {
  return useQuery<PageResponse<AdminAccount>>({
    queryKey: ["admin-accounts", ownerType, page, size],
    queryFn: () =>
      api
        .get("/api/admin/credit/accounts", {
          params: { ownerType, page, size },
        })
        .then((r) => r.data),
  });
}
