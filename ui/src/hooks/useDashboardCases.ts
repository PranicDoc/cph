import { keepPreviousData, useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import type { DashboardCase } from "../types/workqueue";
import type { SpringPageResponse } from "../types/pagination";

export const DASHBOARD_CASES_PAGE_SIZE = 5;

export function useDashboardCases(healerId: string, q: string, page: number) {
  return useQuery<SpringPageResponse<DashboardCase>>({
    queryKey: ["dashboard-cases", healerId, q, page],
    queryFn: () =>
      api
        .get("/api/v1/dashboard/cases", {
          params: { q: q || undefined, page, size: DASHBOARD_CASES_PAGE_SIZE },
        })
        .then((r) => r.data),
    placeholderData: keepPreviousData,
    staleTime: 60 * 1000,
  });
}
