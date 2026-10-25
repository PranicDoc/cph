import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import type { PageResponse } from "./admin/useAdminAccounts";
import type { AdminTransaction } from "./admin/useAdminTransactions";

/** The signed-in healer's own credit history (GET /api/credits/transactions; the server picks the account). */
export function useMyCreditTransactions(page = 0, size = 10) {
  return useQuery<PageResponse<AdminTransaction>>({
    queryKey: ["my-credit-transactions", page, size],
    queryFn: () =>
      api.get("/api/credits/transactions", { params: { page, size } }).then((r) => r.data.data),
  });
}

/** "MONTHLY_RESET" -> "Monthly reset". */
export const transactionLabel = (type: string) =>
  type.charAt(0) + type.slice(1).toLowerCase().replace(/_/g, " ");
