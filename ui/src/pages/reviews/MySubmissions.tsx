import { useEffect } from "react";
import { Link } from "react-router-dom";
import { useEventStream } from "../../hooks/useEventStream";
import { useMySubmissions } from "../../hooks/useMySubmissions";
import { ReviewStatusBadge } from "./ReviewStatusBadge";
import type { SubmissionResponse } from "../../types/review";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

function SubmissionRow({ s }: { s: SubmissionResponse }) {
  return (
    <li data-testid="submission-row" className="space-y-2 rounded-lg border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-medium">Submitted {formatDate(s.createdAt)}</span>
        <ReviewStatusBadge status={s.status} />
      </div>
      <ul className="space-y-0.5 text-xs text-muted-foreground">
        {s.revisions.map((r) => (
          <li key={r.revisionNo}>
            Version {r.revisionNo} · {formatDate(r.submittedAt)} · {r.submittedVia === "AUTO" ? "Sent automatically" : "Submitted by you"}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-4 text-sm">
        <Link to={`/submissions/${s.id}`} className="underline">
          Open submission
        </Link>
        <Link to={`/cases/${s.caseId}/summary`} className="underline">
          View case
        </Link>
      </div>
    </li>
  );
}

/**
 * The healer's own review history: each submission with its status and every version sent. Live:
 * stream events refetch it, and opening the page clears the nav dot.
 */
export default function MySubmissions() {
  const { unreadCount, resetUnread } = useEventStream();
  // Also when an event arrives while the page is open: nothing here is left unseen.
  useEffect(() => resetUnread(), [resetUnread, unreadCount]);

  const { data, isLoading, isError } = useMySubmissions();

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4 p-4">
      <h1 className="text-xl font-semibold">My submissions</h1>
      {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
      {isError && (
        <p role="alert" className="text-sm text-red-500">
          Could not load your submissions.
        </p>
      )}
      {data && data.length === 0 && (
        <p className="text-sm text-muted-foreground">You haven't submitted any cases for review yet.</p>
      )}
      <ul className="space-y-2">
        {data?.map((s) => <SubmissionRow key={s.id} s={s} />)}
      </ul>
    </div>
  );
}
