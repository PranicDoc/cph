import { useQuery } from "@tanstack/react-query";
import api from "../../lib/axios";
import { allReviewsKey } from "../../lib/reviewQueryKeys";
import type { ReviewStatus } from "../../types/review";

/** One reviewer-stage submission, whoever it is assigned to, oldest latest-revision first. */
export interface AdminReviewItem {
  submissionId: string;
  healerId: string;
  healerName: string | null;
  /** Null when the submission has no reviewer; the name alone is null when that account is gone. */
  reviewerId: string | null;
  reviewerName: string | null;
  caseTitle: string;
  latestRevisionNo: number;
  status: ReviewStatus;
  submittedAt: string | null;
}

export interface AllReviewsFilters {
  status?: ReviewStatus;
  reviewerId?: string;
  healerId?: string;
}

/** Every submission, filtered by the server. The route is SUPADM-only server-side. */
export function useAllReviews({ status, reviewerId, healerId }: AllReviewsFilters = {}) {
  return useQuery<AdminReviewItem[]>({
    queryKey: allReviewsKey(status, reviewerId, healerId),
    queryFn: () =>
      api.get("/api/v1/admin/reviews", { params: { status, reviewerId, healerId } }).then((r) => r.data),
  });
}
