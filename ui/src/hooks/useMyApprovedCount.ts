import { useQuery } from "@tanstack/react-query";
import { useCaseReviewEnabled } from "./useCaseReviewEnabled";
import api from "../lib/axios";
import { APPROVED_COUNT_KEY } from "../lib/reviewQueryKeys";

/**
 * How many of the healer's cases are approved, for the dashboard tile. Refetched by the live review
 * stream, never polled. The request needs the case-update permission: for a healer without it the
 * query fails and the dashboard simply leaves the tile out.
 */
export function useMyApprovedCount() {
  const enabled = useCaseReviewEnabled();
  return useQuery<number>({
    queryKey: APPROVED_COUNT_KEY,
    queryFn: () => api.get<{ count: number }>("/api/v1/reviews/my-approved-count").then((r) => r.data.count),
    enabled,
    retry: false, // a healer without the permission gets a 403 that retrying cannot fix
    staleTime: 5 * 60 * 1000,
  });
}
