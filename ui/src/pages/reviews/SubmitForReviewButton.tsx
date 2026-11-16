import { useState } from "react";
import { Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import { NO_REVIEWER_MESSAGE, useSubmitForReview } from "../../hooks/useSubmitForReview";
import type { CaseReviewStatus } from "../../types/review";

interface Props {
  caseId: string;
  status: CaseReviewStatus;
}

/**
 * Sends a case's PDF to the healer's instructor. An approved case has nothing to submit, so the
 * button is not rendered; the healer edits the case first, which dismisses the approval. A refusal
 * from the server is toasted by the hook; the inline line is only the no-instructor pre-check.
 */
export function SubmitForReviewButton({ caseId, status }: Props) {
  const [message, setMessage] = useState<string | null>(null);

  const mutation = useSubmitForReview(caseId);

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
