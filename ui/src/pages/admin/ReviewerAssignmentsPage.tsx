import { useMemo } from "react";

import { useAdminHealers } from "../../hooks/admin/useAdminHealers";
import {
  useAssignReviewer,
  useEligibleReviewers,
  useReviewerAssignments,
  useUnassignReviewer,
} from "../../hooks/admin/useReviewerAssignments";
import { Button } from "@/components/ui/button";
import { SingleSelect } from "@/components/ui/single-select";

/**
 * SUPADM links each healer to the instructor who reviews their cases. The picker offers only
 * active healers who hold CASE:REVIEW (the server returns exactly that list), never the healer
 * being linked.
 */
export default function ReviewerAssignmentsPage() {
  const healers = useAdminHealers();
  const assignments = useReviewerAssignments();
  const eligible = useEligibleReviewers();
  const assign = useAssignReviewer();
  const unassign = useUnassignReviewer();

  const reviewerByHealer = useMemo(
    () => new Map((assignments.data ?? []).map((a) => [a.healerId, a])),
    [assignments.data]
  );

  const rows = (healers.data ?? []).filter((h) => h.active);
  const isLoading = healers.isLoading || assignments.isLoading || eligible.isLoading;
  const isError = healers.isError || assignments.isError || eligible.isError;

  return (
    <div>
      <div className="mb-4 md:mb-6">
        <h1 className="text-xl md:text-2xl font-bold text-text">Reviewer assignments</h1>
        <p className="text-sm text-text-light mt-1">
          Link each healer to the instructor who reviews their cases. Only instructors who hold the
          case-review permission can be chosen.
        </p>
      </div>

      {isLoading && <p className="text-text-light">Loading…</p>}
      {isError && <p className="text-red-500">Failed to load reviewer assignments.</p>}

      {!isLoading && !isError && (
        <ul className="divide-y divide-border border border-border rounded-xl">
          {rows.map((healer) => {
            const link = reviewerByHealer.get(healer.id);
            const options = (eligible.data ?? [])
              .filter((r) => r.id !== healer.id)
              .map((r) => ({ value: r.id, label: r.fullName }));
            return (
              <li
                key={healer.id}
                className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="font-semibold text-text truncate">{healer.fullName}</p>
                  <p className="text-xs text-text-light truncate">{healer.email}</p>
                  <p className="text-sm mt-1" data-testid={`reviewer-${healer.id}`}>
                    {link ? (
                      <>Reviewer: <span className="font-medium">{link.reviewerName ?? "Unknown"}</span></>
                    ) : (
                      <span className="text-text-light">No instructor linked</span>
                    )}
                  </p>
                  {link && (
                    <p className="text-xs text-text-light mt-0.5" data-testid={`approved-${healer.id}`}>
                      Approved cases: {link.approvedCount ?? 0}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-2 sm:w-80">
                  <SingleSelect
                    aria-label={`Instructor for ${healer.fullName}`}
                    value={link?.reviewerId ?? ""}
                    onChange={(reviewerId) => assign.mutate({ healerId: healer.id, reviewerId })}
                    options={options}
                    placeholder="Choose an instructor"
                    disabled={assign.isPending || options.length === 0}
                  />
                  {link && (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={unassign.isPending}
                      onClick={() => unassign.mutate(healer.id)}
                      aria-label={`Unlink instructor from ${healer.fullName}`}
                    >
                      Unlink
                    </Button>
                  )}
                </div>
              </li>
            );
          })}
          {rows.length === 0 && <li className="p-4 text-text-light">No active healers.</li>}
        </ul>
      )}
    </div>
  );
}
