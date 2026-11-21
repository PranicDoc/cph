import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../lib/axios";
import { CASE_STATUSES_KEY } from "../lib/reviewQueryKeys";
import { extractDetail } from "../lib/utils";
import type { SubmissionResponse } from "../types/review";

/** Shown when a healer tries to submit without a linked instructor. */
export const NO_REVIEWER_MESSAGE =
  "You don't have an instructor linked yet. Ask your administrator to link one before submitting.";

/** Opens a review for the case, or adds a revision when one is already open. */
export function useSubmitForReview(caseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<SubmissionResponse>(`/api/v1/reviews/cases/${caseId}/submit`).then((r) => r.data),
    onSuccess: (submission) => {
      if (submission?.outcome === "UNCHANGED") {
        toast.info(`Nothing has changed since version ${submission.latestRevisionNo}, so there is nothing new to review.`);
      } else if (submission?.outcome === "REREQUESTED") {
        toast.success("Review requested again.");
      } else {
        toast.success("Submitted for review");
      }
      queryClient.invalidateQueries({ queryKey: CASE_STATUSES_KEY });
    },
    onError: (e) => toast.error(extractDetail(e) || "Could not submit this case for review."),
  });
}
