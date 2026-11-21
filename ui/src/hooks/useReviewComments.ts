import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../lib/axios";
import { reviewCommentsKey } from "../lib/reviewQueryKeys";
import { extractDetail } from "../lib/utils";
import type { ReviewComment, ReviewSide } from "../types/review";

export interface NewComment {
  revisionNo?: number;
  parentId?: string;
  page?: number;
  x?: number;
  y?: number;
  body: string;
}

const commentsPath = (side: ReviewSide, submissionId: string) =>
  side === "reviewer"
    ? `/api/v1/reviews/inbox/${submissionId}/comments`
    : `/api/v1/reviews/submissions/${submissionId}/comments`;

/**
 * Comments of one submission plus the actions on them. Live updates arrive through the review
 * stream, which invalidates every ["review", submissionId, ...] key, this one included.
 */
export function useReviewComments(side: ReviewSide, submissionId: string) {
  const queryClient = useQueryClient();
  const key = reviewCommentsKey(submissionId, side);

  const comments = useQuery<ReviewComment[]>({
    queryKey: key,
    queryFn: () => api.get(commentsPath(side, submissionId)).then((r) => r.data),
    enabled: !!submissionId,
    retry: false,
  });

  const add = useMutation({
    mutationFn: (c: NewComment) => api.post<ReviewComment>(commentsPath(side, submissionId), c).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
    onError: (e) => toast.error(extractDetail(e) || "Could not post the comment."),
  });

  // Instructor only.
  const resolve = useMutation({
    mutationFn: (commentId: string) => api.post(`/api/v1/reviews/inbox/comments/${commentId}/resolve`).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
    onError: (e) => toast.error(extractDetail(e) || "Could not resolve the thread."),
  });

  return { comments, add: add.mutateAsync, resolve: resolve.mutate };
}
