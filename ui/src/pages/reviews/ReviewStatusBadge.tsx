import { cn } from "@/lib/utils";
import type { CaseReviewStatus } from "./reviewApi";

export const REVIEW_STATUS_LABELS: Record<CaseReviewStatus, string> = {
  NOT_SUBMITTED: "Not submitted",
  NOT_SUBMITTED_NO_REVIEWER: "Not submitted — no instructor linked",
  PENDING_REVIEW: "In review",
  CHANGES_REQUESTED: "Changes requested",
  APPROVED: "Approved",
  DISMISSED: "Dismissed — submit again",
};

const TONES: Record<CaseReviewStatus, string> = {
  NOT_SUBMITTED: "bg-muted text-text-light",
  NOT_SUBMITTED_NO_REVIEWER: "bg-muted text-text-light",
  PENDING_REVIEW: "bg-primary/10 text-primary",
  CHANGES_REQUESTED: "bg-amber-100 text-amber-900",
  APPROVED: "bg-green-100 text-green-800",
  DISMISSED: "bg-red-100 text-red-800",
};

/** A case's review state as a small pill; the label says what the healer should do next. */
export function ReviewStatusBadge({ status, className }: { status: CaseReviewStatus; className?: string }) {
  return (
    <span
      className={cn(
        "inline-block whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium",
        TONES[status],
        className
      )}
    >
      {REVIEW_STATUS_LABELS[status]}
    </span>
  );
}
