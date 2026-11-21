import { isAxiosError } from "axios";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../lib/axios";
import { REVIEW_INBOX_KEY, reviewInboxItemKey } from "../lib/reviewQueryKeys";
import { extractDetail } from "../lib/utils";
import type { SubmissionResponse } from "../types/review";

/**
 * Approve or request changes on the latest revision. A 409 means a newer version arrived: it is not
 * toasted, the screen shows the server's reason as a banner from `error`. Any other failure toasts.
 */
export function useReviewDecision(submissionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ kind, revisionNo }: { kind: "approve" | "request-changes"; revisionNo: number }) =>
      api
        .post<SubmissionResponse>(`/api/v1/reviews/inbox/${submissionId}/${kind}`, { revisionNo })
        .then((r) => r.data),
    onError: (e) => {
      if (isAxiosError(e) && e.response?.status === 409) return;
      toast.error(extractDetail(e) || "Could not save your decision. Try again.");
    },
    onSettled: () => {
      // A refusal usually means a newer version arrived: the refetch brings up the refresh banner.
      queryClient.invalidateQueries({ queryKey: reviewInboxItemKey(submissionId) });
      queryClient.invalidateQueries({ queryKey: REVIEW_INBOX_KEY });
    },
  });
}
