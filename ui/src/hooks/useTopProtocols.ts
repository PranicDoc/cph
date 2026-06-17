import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { ChartPeriod, periodToApiParams } from "../types/chart";

export interface TopProtocolPoint {
  protocol: string;
  count: number;
}

export function useTopProtocols(healerId: string, period: ChartPeriod) {
  return useQuery<TopProtocolPoint[]>({
    queryKey: ["top-protocols", healerId, period],
    queryFn: () =>
      api
        .get("/api/v1/dashboard/top-protocols", {
          params: periodToApiParams(period),
        })
        .then((r) => r.data),
    staleTime: 5 * 60 * 1000,
  });
}
