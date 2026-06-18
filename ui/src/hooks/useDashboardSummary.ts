import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";

export interface DashboardSummaryResponse {
  activePatients: number;
  sessionsThisMonth: number;
  creditsRemaining: number | null;
  pdfsGeneratedThisMonth: number;
  creditServiceUnavailable: boolean;
}

export function useDashboardSummary() {
  return useQuery<DashboardSummaryResponse>({
    queryKey: ["dashboard-summary"],
    queryFn: () => api.get("/api/v1/dashboard/summary").then((r) => r.data?.data),
    staleTime: 5 * 60 * 1000,
  });
}
