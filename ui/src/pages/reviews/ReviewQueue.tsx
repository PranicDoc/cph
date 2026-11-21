import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useEventStream } from "../../hooks/useEventStream";
import { reviewInboxKey } from "../../lib/reviewQueryKeys";
import { inbox, STATUS_LABELS, type ReviewStatus } from "./reviewApi";

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "";
}

/** The instructor's queue of submitted cases, oldest first. Live: stream events refetch it. */
export default function ReviewQueue() {
  const [status, setStatus] = useState<ReviewStatus | "">("");
  const { unreadCount, resetUnread } = useEventStream();
  // Also when an event arrives while the queue is open: it is already on screen.
  useEffect(() => resetUnread(), [resetUnread, unreadCount]);

  const { data, isLoading, isError } = useQuery({
    queryKey: reviewInboxKey(status || undefined),
    queryFn: () => inbox(status || undefined),
  });

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-xl font-semibold">Reviews</h1>
        <label className="flex items-center gap-2 text-sm">
          Status
          <select
            className="rounded border bg-background px-2 py-1"
            value={status}
            onChange={(e) => setStatus(e.target.value as ReviewStatus | "")}
          >
            <option value="">All</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
      {isError && (
        <p role="alert" className="text-sm text-red-500">
          Could not load your review queue.
        </p>
      )}
      {data && data.length === 0 && <p className="text-sm text-muted-foreground">No submissions to review.</p>}
      <ul className="space-y-2">
        {data?.map((item) => (
          <li key={item.submissionId}>
            <Link
              to={`/reviews/${item.submissionId}`}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3 hover:bg-muted/50"
            >
              <span className="min-w-0">
                <span className="block truncate font-medium">{item.healerName}</span>
                <span className="block truncate text-sm text-muted-foreground">{item.caseTitle}</span>
              </span>
              <span className="text-right text-xs text-muted-foreground">
                <span className="block">{STATUS_LABELS[item.status]}</span>
                <span className="block">
                  Version {item.latestRevisionNo} · {formatDate(item.submittedAt)}
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
