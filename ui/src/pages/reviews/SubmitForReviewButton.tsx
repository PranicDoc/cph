import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { extractDetail } from "../../lib/utils";
import { CASE_STATUSES_KEY } from "../../lib/reviewQueryKeys";
import { NO_REVIEWER_MESSAGE, submitCase, type CaseReviewStatus } from "./reviewApi";

interface Props {
  caseId: string;
  status: CaseReviewStatus;
}

/**
 * Sends a case's PDF to the healer's instructor. An approved case has nothing to submit, so the
 * button is not rendered; the healer edits the case first, which dismisses the approval.
 */
export function SubmitForReviewButton({ caseId, status }: Props) {
  const queryClient = useQueryClient();
  const [message, setMessage] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => submitCase(caseId),
    onSuccess: (submission) => {
      setMessage(null);
      if (submission?.outcome === "UNCHANGED") {
        toast.info(`Nothing has changed since version ${submission.latestRevisionNo}, so there is nothing new to review.`);
      } else if (submission?.outcome === "REREQUESTED") {
        toast.success("Review requested again.");
      } else {
        toast.success("Submitted for review");
      }
      queryClient.invalidateQueries({ queryKey: CASE_STATUSES_KEY });
    },
    onError: (err) => setMessage(extractDetail(err) ?? "Could not submit this case for review."),
  });

  if (status === "APPROVED") return null;

  const resubmit = status === "PENDING_REVIEW" || status === "CHANGES_REQUESTED";
  const onClick = () => {
    if (status === "NOT_SUBMITTED_NO_REVIEWER") {
      setMessage(NO_REVIEWER_MESSAGE);
      return;
    }
    setMessage(null);
    mutation.mutate();
  };

  return (
    <div className="flex flex-col items-end gap-1">
      <Button type="button" size="sm" variant="outline" disabled={mutation.isPending} onClick={onClick}>
        <Send className="w-3.5 h-3.5" />
        {resubmit ? "Resubmit for review" : "Submit for review"}
      </Button>
      {message && (
        <p role="alert" className="max-w-xs text-right text-xs text-red-500">
          {message}
        </p>
      )}
    </div>
  );
}
