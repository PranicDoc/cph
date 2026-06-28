import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { FollowupPatient } from "../types/workqueue";

export function useFollowups(healerId: string) {
  return useQuery<FollowupPatient[]>({
    queryKey: ["followups", healerId],
    queryFn: () =>
      api.get("/api/v1/dashboard/followups").then((r) => r.data?.data),
    staleTime: 5 * 60 * 1000,
  });
}
