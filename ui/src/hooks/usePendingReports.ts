import { keepPreviousData, useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import type { PendingReport } from "../types/workqueue";
import type { SpringPageResponse } from "../types/pagination";

export const PENDING_REPORTS_PAGE_SIZE = 10;

export function usePendingReports(healerId: string, q: string, page: number) {
  return useQuery<SpringPageResponse<PendingReport>>({
    queryKey: ["pending-reports", healerId, q, page],
    queryFn: () =>
      api
        .get("/api/v1/dashboard/pending-reports/page", {
          params: { q: q || undefined, page, size: PENDING_REPORTS_PAGE_SIZE },
        })
        .then((r) => r.data),
    placeholderData: keepPreviousData,
    staleTime: 60 * 1000,
  });
}
