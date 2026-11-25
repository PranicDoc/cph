import { useState } from "react";
import { Link } from "react-router-dom";

import { useAuth } from "../../context/AuthContext";
import { useAllReviews, type AdminReviewItem } from "../../hooks/admin/useAllReviews";
import {
  useAssignReviewer,
  useEligibleReviewers,
  useReviewerAssignments,
} from "../../hooks/admin/useReviewerAssignments";
import type { ReviewStatus } from "../../types/review";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ReviewStatusBadge } from "../reviews/ReviewStatusBadge";
import StatusFilter from "../reviews/StatusFilter";
import { reviewerLabel } from "../reviews/submissions";
import SuperAdminBanner from "../reviews/SuperAdminBanner";
import { formatWhen, PAGE, SURFACE, TABLE } from "../reviews/ui";
import { AssignReviewerDialog } from "./AssignReviewerDialog";

const isOpen = (status: ReviewStatus) => status === "PENDING_REVIEW" || status === "CHANGES_REQUESTED";

/** One option per person, by name. */
function people(rows: [string | null, string | null][]): [string, string][] {
  const byId = new Map<string, string>();
  rows.forEach(([id, name]) => id && byId.set(id, name || "Unknown"));
  return [...byId].sort((a, b) => a[1].localeCompare(b[1]));
}

function PersonFilter({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: [string, string][];
  onChange: (id: string) => void;
}) {
  return (
    <label className="ongr flex items-center gap-2 text-sm max-[760px]:w-full">
      <span>{label}</span>
      <select
        className="h-10 min-w-0 max-w-[16rem] rounded-lg border bg-[var(--color-card)] px-2 text-foreground max-[760px]:max-w-none max-[760px]:flex-1"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">All</option>
        {options.map(([id, name]) => (
          <option key={id} value={id}>
            {name}
          </option>
        ))}
      </select>
    </label>
  );
}

function ReviewRow({ item, onReassign }: { item: AdminReviewItem; onReassign: () => void }) {
  const healerName = item.healerName || "Unknown";
  return (
    <tr className={TABLE.row}>
      <td className={TABLE.title}>
        <div className="name break-words font-semibold">{item.caseTitle}</div>
        <div className="muted text-xs tabular-nums text-muted-foreground">R{item.latestRevisionNo}</div>
      </td>
      <td className={TABLE.cell} data-label="Healer">
        {healerName}
      </td>
      <td className={TABLE.cell} data-label="Assigned reviewer">
        {reviewerLabel(item.reviewerId, item.reviewerName)}
      </td>
      <td className={cn(TABLE.cell, "tabular-nums")} data-label="Submitted">
        {formatWhen(item.submittedAt)}
      </td>
      <td className={TABLE.cell} data-label="Status">
        <ReviewStatusBadge status={item.status} label={item.status === "DISMISSED" ? "Dismissed" : undefined} />
      </td>
      <td className={TABLE.actions} data-label="">
        <div className="flex justify-end gap-2">
          <Button type="button" size="sm" variant="outline" onClick={onReassign} aria-label={`Reassign ${healerName}`}>
            Reassign
          </Button>
          <Button asChild size="sm" variant={item.status === "PENDING_REVIEW" ? "default" : "outline"}>
            <Link to={`/reviews/${item.submissionId}`}>{isOpen(item.status) ? "Open review" : "View"}</Link>
          </Button>
        </div>
      </td>
    </tr>
  );
}

/** SUPADM only: every submitted case sheet, whoever it is assigned to. */
export default function AllReviewsPage() {
  const { user } = useAuth();
  const [status, setStatus] = useState<ReviewStatus | "">("");
  const [reviewerId, setReviewerId] = useState("");
  const [healerId, setHealerId] = useState("");
  const [target, setTarget] = useState<{ id: string; fullName: string } | null>(null);

  const chosenPeople = { reviewerId: reviewerId || undefined, healerId: healerId || undefined };
  const list = useAllReviews({ status: status || undefined, ...chosenPeople });
  // Same request as the list while no status is chosen, and as `everything` while nothing is.
  const counted = useAllReviews(chosenPeople).data;
  const everything = useAllReviews().data ?? [];
  const assignments = useReviewerAssignments();
  const eligible = useEligibleReviewers();
  const assign = useAssignReviewer();

  // The picker changes the healer's link, which can differ from the reviewer stored on a closed submission.
  const linkedReviewer = assignments.data?.find((a) => a.healerId === target?.id)?.reviewerId;

  return (
    <div className={PAGE}>
      <div className="page-head">
        <div>
          <h1 className="text-2xl font-semibold">All reviews</h1>
          <p className="muted mt-1 text-sm">Every submitted case sheet, whoever it is assigned to.</p>
        </div>
      </div>

      <SuperAdminBanner />

      <div className="flex flex-wrap items-center gap-3">
        <StatusFilter value={status} onChange={setStatus} items={counted} />
        <PersonFilter
          label="Reviewer"
          value={reviewerId}
          options={people(everything.map((i) => [i.reviewerId, i.reviewerName]))}
          onChange={setReviewerId}
        />
        <PersonFilter
          label="Healer"
          value={healerId}
          options={people(everything.map((i) => [i.healerId, i.healerName]))}
          onChange={setHealerId}
        />
      </div>

      {list.isLoading && <p className="ongr text-sm text-muted-foreground">Loading…</p>}
      {list.isError && (
        <p role="alert" className="ongr text-sm text-destructive">
          Could not load the reviews.
        </p>
      )}
      {eligible.isError && (
        <p role="alert" className="ongr text-sm text-destructive">
          Could not load the accounts to reassign to.
        </p>
      )}
      {list.data && list.data.length === 0 && (
        <p className="ongr text-sm text-muted-foreground">No submissions match.</p>
      )}
      {list.data && list.data.length > 0 && (
        <div className={cn(SURFACE, "t-wrap")}>
          <table className={TABLE.table}>
            <thead className={TABLE.head}>
              <tr>
                {["Case", "Healer", "Assigned reviewer", "Submitted", "Status"].map((h) => (
                  <th key={h} className={TABLE.th}>
                    {h}
                  </th>
                ))}
                <th className={TABLE.th}>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className={TABLE.body}>
              {list.data.map((item) => (
                <ReviewRow
                  key={item.submissionId}
                  item={item}
                  onReassign={() => setTarget({ id: item.healerId, fullName: item.healerName || "Unknown" })}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Waits for the links so the current reviewer is preselected from the start. */}
      {target && !assignments.isLoading && (
        <AssignReviewerDialog
          key={target.id}
          healer={target}
          candidates={eligible.data ?? []}
          loading={eligible.isLoading}
          note="This reassigns all of this healer's open reviews."
          currentReviewerId={linkedReviewer ?? undefined}
          currentUserId={user?.id}
          pending={assign.isPending}
          onAssign={(chosen) => {
            const healerId = target.id;
            assign.mutate(
              { healerId, reviewerId: chosen },
              { onSuccess: () => setTarget((t) => (t?.id === healerId ? null : t)) }
            );
          }}
          onClose={() => setTarget(null)}
        />
      )}
    </div>
  );
}
