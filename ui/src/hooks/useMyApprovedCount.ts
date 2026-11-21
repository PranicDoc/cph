import { useQuery } from "@tanstack/react-query";
import { APPROVED_COUNT_KEY } from "../lib/reviewQueryKeys";
import { myApprovedCount } from "../pages/reviews/reviewApi";

/**
 * How many of the healer's cases are approved, for the dashboard tile. Refetched by the live review
 * stream, never polled. The request needs the case-update permission: for a healer without it the
 * query fails and the dashboard simply leaves the tile out.
 */
export function useMyApprovedCount() {
  return useQuery({ queryKey: APPROVED_COUNT_KEY, queryFn: myApprovedCount, retry: false, staleTime: 5 * 60 * 1000 });
}
