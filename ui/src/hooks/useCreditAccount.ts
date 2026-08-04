import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";

export interface CreditAccount {
  account_id: string;
  owner_type: string;
  owner_id: string;
  free_balance: number;
  paid_balance: number;
  plan_tier: string;
}

// POST /api/credits/account is an idempotent find-or-create scoped to the
// caller's own healer account (server-side, not client-supplied), so it's
// safe to call as a plain "get my account" query.
export function useCreditAccount() {
  return useQuery<CreditAccount>({
    queryKey: ["credit-account"],
    queryFn: () => api.post("/api/credits/account").then((r) => r.data.data),
  });
}
