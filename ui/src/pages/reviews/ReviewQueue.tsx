import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useEventStream } from "../../hooks/useEventStream";
import { useReviewInbox } from "../../hooks/useReviewInbox";
import type { ReviewStatus } from "../../types/review";
import { ReviewStatusBadge } from "./ReviewStatusBadge";
import { formatWhen, PAGE, SURFACE, TABLE } from "./ui";

const FILTERS: { value: ReviewStatus | ""; label: string }[] = [
  { value: "", label: "All" },
  { value: "PENDING_REVIEW", label: "In review" },
  { value: "CHANGES_REQUESTED", label: "Changes requested" },
  { value: "APPROVED", label: "Approved" },
  { value: "DISMISSED", label: "Dismissed" },
];

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
          <div
            className="seg inline-flex max-w-full gap-0.5 self-start overflow-x-auto rounded-xl bg-muted p-1"
            role="group"
            aria-label="Filter by status"
          >
            {FILTERS.map((f) => {
              const count = all && (f.value ? all.filter((i) => i.status === f.value).length : all.length);
              return (
                <button
                  key={f.value}
                  type="button"
                  aria-pressed={status === f.value}
                  className={cn(
                    "whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium max-[760px]:!flex-[1_0_auto]",
                    status === f.value && "on bg-[var(--color-card)] shadow-sm"
                  )}
                  onClick={() => setStatus(f.value)}
                >
                  {f.label}
                  {count !== undefined && " "}
                  {count !== undefined && <span className="tabular-nums">{count}</span>}
                </button>
              );
            })}
          </div>
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
