import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { PendingReport } from "../types/workqueue";

export function usePendingReports(healerId: string) {
  return useQuery<PendingReport[]>({
    queryKey: ["pending-reports", healerId],
    queryFn: () =>
      api.get("/api/v1/dashboard/pending-reports").then((r) => r.data?.data),
    staleTime: 5 * 60 * 1000,
  });
}
