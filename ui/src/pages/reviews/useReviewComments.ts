import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { reviewCommentsKey } from "../../lib/reviewQueryKeys";
import {
  addComment,
  listComments,
  resolveComment,
  reviewErrorMessage,
  type NewComment,
  type ReviewSide,
} from "./reviewApi";

/**
 * Comments of one submission plus the actions on them. Live updates arrive through the review
 * stream, which invalidates every ["review", submissionId, ...] key, this one included.
 */
export function useReviewComments(side: ReviewSide, submissionId: string) {
  const queryClient = useQueryClient();
  const key = reviewCommentsKey(submissionId, side);
  const [error, setError] = useState<string | null>(null);

  const comments = useQuery({
    queryKey: key,
    queryFn: () => listComments(side, submissionId),
    enabled: !!submissionId,
    retry: false,
  });

  const add = useMutation({
    mutationFn: (c: NewComment) => addComment(side, submissionId, c),
    onSuccess: () => {
      setError(null);
      return queryClient.invalidateQueries({ queryKey: key });
    },
    onError: (e) => setError(reviewErrorMessage(e, "Could not post the comment.")),
  });

  const resolve = useMutation({
    mutationFn: (commentId: string) => resolveComment(commentId),
    onSuccess: () => {
      setError(null);
      return queryClient.invalidateQueries({ queryKey: key });
    },
    onError: (e) => setError(reviewErrorMessage(e, "Could not resolve the thread.")),
  });

  return { comments, add: add.mutateAsync, resolve: resolve.mutate, error };
}
