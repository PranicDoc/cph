import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { ChartPeriod, periodToApiParams } from "../types/chart";

export interface CreditUsagePoint {
  date: string;
  creditsConsumed: number;
}

export function useCreditUsage(healerId: string, period: ChartPeriod) {
  return useQuery<CreditUsagePoint[]>({
    queryKey: ["credit-usage", healerId, period],
    queryFn: () =>
      api
        .get("/api/v1/dashboard/credit-usage", {
          params: periodToApiParams(period),
        })
        .then((r) => r.data),
    staleTime: 5 * 60 * 1000,
  });
}
