import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { ChartPeriod, periodToApiParams } from "../types/chart";

export interface SessionTrendPoint {
  date: string;
  count: number;
}

export function useSessionTrends(healerId: string, period: ChartPeriod) {
  return useQuery<SessionTrendPoint[]>({
    queryKey: ["session-trends", healerId, period],
    queryFn: () =>
      api
        .get("/api/v1/dashboard/session-trends", {
          params: periodToApiParams(period),
        })
        .then((r) => r.data?.data),
    staleTime: 5 * 60 * 1000,
  });
}
