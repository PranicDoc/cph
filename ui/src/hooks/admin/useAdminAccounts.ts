import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../../lib/axios";
import { extractDetail } from "../../lib/utils";
import type { ValidityUnit } from "../../lib/validity";

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

export interface CustomPlanPayload {
  /** Blank = CUSTOM. */
  plan_tag?: string;
  credits: number;
  /** 0 = no expiry date */
  validity_value: number;
  validity_unit?: ValidityUnit;
  notes?: string;
}

/** SUPADM only: replaces the healer's plan with a tagged custom plan (credits reset, new expiry). */
export function useGrantCustomPlan() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ healerId, payload }: { healerId: string; payload: CustomPlanPayload }) =>
      api
        .post<AdminAccount>(`/api/admin/credit/accounts/HEALER/${healerId}/custom-plan`, payload)
        .then((r) => r.data),
    onSuccess: (acct) => {
      qc.invalidateQueries({ queryKey: ["admin-accounts"] });
      qc.invalidateQueries({ queryKey: ["admin-transactions"] });
      toast.success(`${acct.plan_tier} plan set`);
    },
    onError: (err) => toast.error(extractDetail(err) ?? "Could not set the plan"),
  });
}
