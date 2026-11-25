import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useEventStream } from "../../hooks/useEventStream";
import { useReviewInbox } from "../../hooks/useReviewInbox";
import type { ReviewStatus } from "../../types/review";
import { ReviewStatusBadge } from "./ReviewStatusBadge";
import StatusFilter from "./StatusFilter";
import { formatWhen, PAGE, SURFACE, TABLE } from "./ui";

/** The instructor's queue of submitted cases, oldest first. Live: stream events refetch it. */
export default function ReviewQueue() {
  const [status, setStatus] = useState<ReviewStatus | "">("");
  const { unreadCount, resetUnread } = useEventStream();
  // Also when an event arrives while the queue is open: it is already on screen.
  useEffect(() => resetUnread(), [resetUnread, unreadCount]);

  const { data, isLoading, isError } = useReviewInbox(status || undefined);
  // The whole queue, for the filter counts; the same request as the list while unfiltered.
  const all = useReviewInbox().data;

  return (
    <div className={PAGE}>
      <div className="page-head">
        <div>
          <h1 className="text-2xl font-semibold">Reviews</h1>
          <p className="muted mt-1 text-sm">Case sheets submitted by the healers you review. Oldest first.</p>
        </div>
        <div className="flex w-full">
          <StatusFilter value={status} onChange={setStatus} items={all} />
        </div>
      </div>

      {isLoading && <p className="ongr text-sm text-muted-foreground">Loading…</p>}
      {isError && (
        <p role="alert" className="ongr text-sm text-destructive">
          Could not load your review queue.
        </p>
      )}
      {data && data.length === 0 && <p className="ongr text-sm text-muted-foreground">No submissions to review.</p>}
      {data && data.length > 0 && (
        <div className={cn(SURFACE, "t-wrap")}>
          <table className={TABLE.table}>
            <thead className={TABLE.head}>
              <tr>
                {["Case", "Healer", "Submitted", "Revision", "Status"].map((h) => (
                  <th key={h} className={TABLE.th}>
                    {h}
                  </th>
                ))}
                <th className={TABLE.th}>
                  <span className="sr-only">Action</span>
                </th>
              </tr>
            </thead>
            <tbody className={TABLE.body}>
              {data.map((item) => (
                <tr key={item.submissionId} className={TABLE.row}>
                  <td className={TABLE.title}>
                    <span className="name break-words font-semibold">{item.caseTitle}</span>
                  </td>
                  <td className={TABLE.cell} data-label="Healer">
                    {item.healerName}
                  </td>
                  <td className={cn(TABLE.cell, "tabular-nums")} data-label="Submitted">
                    {formatWhen(item.submittedAt)}
                  </td>
                  <td className={cn(TABLE.cell, "tabular-nums")} data-label="Revision">
                    R{item.latestRevisionNo}
                  </td>
                  <td className={TABLE.cell} data-label="Status">
                    <ReviewStatusBadge
                      status={item.status}
                      label={item.status === "DISMISSED" ? "Dismissed" : undefined}
                    />
                  </td>
                  <td className={TABLE.actions} data-label="">
                    <Button asChild size="sm" variant={item.status === "PENDING_REVIEW" ? "default" : "outline"}>
                      <Link to={`/reviews/${item.submissionId}`}>
                        {item.status === "PENDING_REVIEW" || item.status === "CHANGES_REQUESTED"
                          ? "Open review"
                          : "View"}
                      </Link>
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
