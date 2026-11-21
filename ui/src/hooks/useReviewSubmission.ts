import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { reviewHealerSubmissionKey, reviewInboxItemKey } from "../lib/reviewQueryKeys";
import type { SubmissionResponse } from "../types/review";

const BASE = "/api/v1/reviews";

/** One assigned submission with its revisions. A 403 means it was moved to another instructor. */
export function useReviewerSubmission(submissionId: string) {
  return useQuery<SubmissionResponse>({
    queryKey: reviewInboxItemKey(submissionId),
    queryFn: () => api.get(`${BASE}/inbox/${submissionId}`).then((r) => r.data),
    enabled: !!submissionId,
    retry: false,
  });
}

/** One of the caller's own submissions with its revisions. */
export function useHealerSubmission(submissionId: string) {
  return useQuery<SubmissionResponse>({
    queryKey: reviewHealerSubmissionKey(submissionId),
    queryFn: () => api.get(`${BASE}/submissions/${submissionId}`).then((r) => r.data),
    enabled: !!submissionId,
    retry: false,
  });
}
