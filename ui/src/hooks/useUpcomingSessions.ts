import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { UpcomingSession } from "../types/workqueue";

export function useUpcomingSessions(healerId: string) {
  return useQuery<UpcomingSession[]>({
    queryKey: ["upcoming-sessions", healerId],
    queryFn: () =>
      api.get("/api/v1/dashboard/upcoming-sessions").then((r) => r.data?.data),
    staleTime: 5 * 60 * 1000,
  });
}
