import { useEffect } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useEventStream } from "../../hooks/useEventStream";
import { useMySubmissions } from "../../hooks/useMySubmissions";
import { ReviewStatusBadge } from "./ReviewStatusBadge";
import { newestByCase } from "./submissions";
import { formatWhen, PAGE, SURFACE, TABLE } from "./ui";
import type { SubmissionResponse } from "../../types/review";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

const needsResubmit = (s: SubmissionResponse) => s.status === "CHANGES_REQUESTED" || s.status === "DISMISSED";

/** A superseded submission is history: it can only be viewed. */
function actionLabel(s: SubmissionResponse, superseded: boolean): string {
  if (superseded || s.status === "APPROVED") return "View";
  return needsResubmit(s) ? "Open and resubmit" : "Open";
}

/** Counts cases, not submissions: a case's newest submission is its current state. */
function SubmissionCounts({ submissions }: { submissions: SubmissionResponse[] }) {
  const current = [...newestByCase(submissions).values()];
  const cards = [
    {
      label: "Approved cases",
      value: current.filter((s) => s.status === "APPROVED").length,
      detail: "Shown on your dashboard",
    },
    {
      label: "Waiting for your instructor",
      value: current.filter((s) => s.status === "PENDING_REVIEW").length,
      detail: "You are notified when they decide",
    },
    {
      label: "Needs your attention",
      value: current.filter(needsResubmit).length,
      detail: "Changes requested or approval set aside",
    },
  ];
  return (
    <div className="kpis grid !grid-cols-3 gap-4 max-[760px]:!grid-cols-1">
      {cards.map((c) => (
        <div key={c.label} role="group" aria-label={c.label} className={cn(SURFACE, "kpi p-4")}>
          <div className="k-l text-xs font-medium text-muted-foreground">{c.label}</div>
          <div className="k-v mt-1 text-3xl font-semibold tabular-nums">{c.value}</div>
          <div className="k-d muted mt-1 text-xs">{c.detail}</div>
        </div>
      ))}
    </div>
  );
}

function SubmissionRow({ s, superseded }: { s: SubmissionResponse; superseded: boolean }) {
  return (
    <tr data-testid="submission-row" className={TABLE.row}>
      <td className={TABLE.title}>
        <div className="name font-semibold">Submitted {formatDate(s.createdAt)}</div>
        <Link to={`/cases/${s.caseId}/summary`} className="text-xs underline">
          View case
        </Link>
      </td>
      <td className={cn(TABLE.cell, "tabular-nums")} data-label="Last update">
        {formatWhen(s.updatedAt)}
      </td>
      <td className={TABLE.cell} data-label="Revisions">
        <div className="py-1 max-[760px]:text-right">
          <span className="tabular-nums">R{s.latestRevisionNo}</span>
          <ul className="space-y-0.5 text-xs text-muted-foreground">
            {s.revisions.map((r) => (
              <li key={r.revisionNo}>
                Version {r.revisionNo} · {formatDate(r.submittedAt)} ·{" "}
                {r.submittedVia === "AUTO" ? "Sent automatically" : "Submitted by you"}
              </li>
            ))}
          </ul>
        </div>
      </td>
      <td className={TABLE.cell} data-label="Status">
        <ReviewStatusBadge status={s.status} />
      </td>
      <td className={TABLE.actions} data-label="">
        <Button asChild size="sm" variant={!superseded && needsResubmit(s) ? "default" : "outline"}>
          <Link to={`/submissions/${s.id}`}>{actionLabel(s, superseded)}</Link>
        </Button>
      </td>
    </tr>
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
  const newest = newestByCase(data ?? []);

  return (
    <div className={PAGE}>
      <div className="page-head">
        <div>
          <h1 className="text-2xl font-semibold">My submissions</h1>
          <p className="muted mt-1 text-sm">
            Case sheets you sent to your instructor. The status tells you what to do next.
          </p>
        </div>
      </div>
      {isLoading && <p className="ongr text-sm text-muted-foreground">Loading…</p>}
      {isError && (
        <p role="alert" className="ongr text-sm text-destructive">
          Could not load your submissions.
        </p>
      )}
      {data && <SubmissionCounts submissions={data} />}
      {data && data.length === 0 && (
        <p className="ongr text-sm text-muted-foreground">You haven't submitted any cases for review yet.</p>
      )}
      {data && data.length > 0 && (
        <div className={cn(SURFACE, "t-wrap")}>
          <table className={TABLE.table}>
            <thead className={TABLE.head}>
              <tr>
                {["Case", "Last update", "Revisions", "Status"].map((h) => (
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
              {data.map((s) => (
                <SubmissionRow key={s.id} s={s} superseded={newest.get(s.caseId) !== s} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
