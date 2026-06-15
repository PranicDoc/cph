import { useQuery } from "@tanstack/react-query";
import api from "../../lib/axios";
import type { PageResponse } from "./useAdminAccounts";

export interface AdminTransaction {
  id: string;
  type: string;
  amount: number;
  balance_after: number;
  reference_id: string | null;
  notes: string | null;
  created_at: string;
}

export function useAdminTransactions(
  ownerType: string,
  ownerId: string,
  page = 0,
  size = 20
) {
  return useQuery<PageResponse<AdminTransaction>>({
    queryKey: ["admin-transactions", ownerType, ownerId, page, size],
    queryFn: () =>
      api
        .get(`/api/admin/credit/accounts/${ownerType}/${ownerId}/transactions`, {
          params: { page, size },
        })
        .then((r) => r.data),
    enabled: !!ownerType && !!ownerId,
  });
}
