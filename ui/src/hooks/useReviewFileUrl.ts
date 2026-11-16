import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { reviewFileKey } from "../lib/reviewQueryKeys";
import type { ReviewSide } from "../types/review";

/** Short-lived presigned URL of one revision's PDF, for the instructor or the submitting healer. */
export function useReviewFileUrl(side: ReviewSide, submissionId: string, revisionNo: number) {
  const base = side === "reviewer" ? "inbox" : "submissions";
  return useQuery<string>({
    queryKey: reviewFileKey(submissionId, revisionNo),
    queryFn: () =>
      api
        .get<{ url: string }>(`/api/v1/reviews/${base}/${submissionId}/revisions/${revisionNo}/file`)
        .then((r) => r.data.url),
    enabled: !!submissionId && revisionNo > 0,
    staleTime: 5 * 60_000,
    retry: false,
  });
}
