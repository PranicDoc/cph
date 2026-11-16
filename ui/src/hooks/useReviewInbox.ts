import { useQuery } from "@tanstack/react-query";
import api from "../lib/axios";
import { reviewInboxKey } from "../lib/reviewQueryKeys";
import type { ReviewStatus } from "../types/review";

/** One row of the instructor's queue, oldest latest-revision first. */
export interface InboxItem {
  submissionId: string;
  healerName: string;
  caseTitle: string;
  latestRevisionNo: number;
  status: ReviewStatus;
  submittedAt: string | null;
}

/** The instructor's queue, optionally filtered by status. Live: stream events refetch it. */
export function useReviewInbox(status?: ReviewStatus) {
  return useQuery<InboxItem[]>({
    queryKey: reviewInboxKey(status),
    queryFn: () => api.get("/api/v1/reviews/inbox", { params: status ? { status } : undefined }).then((r) => r.data),
  });
}
