import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { ChartPeriod, periodToApiParams } from "../types/chart";

export interface PatientGrowthPoint {
  date: string;
  count: number;
}

export function usePatientGrowth(healerId: string, period: ChartPeriod) {
  return useQuery<PatientGrowthPoint[]>({
    queryKey: ["patient-growth", healerId, period],
    queryFn: () =>
      api
        .get("/api/v1/dashboard/patient-growth", {
          params: periodToApiParams(period),
        })
        .then((r) => r.data?.data),
    staleTime: 5 * 60 * 1000,
  });
}
