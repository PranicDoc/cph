import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { ChartPeriod, periodToApiParams } from "../types/chart";

export interface TopSymptomPoint {
  symptom: string;
  count: number;
}

export function useTopSymptoms(healerId: string, period: ChartPeriod) {
  return useQuery<TopSymptomPoint[]>({
    queryKey: ["top-symptoms", healerId, period],
    queryFn: () =>
      api
        .get("/api/v1/dashboard/top-symptoms", {
          params: periodToApiParams(period),
        })
        .then((r) => r.data),
    staleTime: 5 * 60 * 1000,
  });
}
