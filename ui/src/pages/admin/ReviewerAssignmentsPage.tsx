import { useMemo, useState } from "react";

import { useAuth } from "../../context/AuthContext";
import { useAdminHealers } from "../../hooks/admin/useAdminHealers";
import {
  useAssignReviewer,
  useEligibleReviewers,
  useReviewerAssignments,
  useUnassignReviewer,
} from "../../hooks/admin/useReviewerAssignments";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AssignReviewerDialog } from "./AssignReviewerDialog";

/** SUPADM chooses who reviews each healer's cases. Any active account can be the reviewer. */
export default function ReviewerAssignmentsPage() {
  const { user } = useAuth();
  const healers = useAdminHealers();
  const assignments = useReviewerAssignments();
  const eligible = useEligibleReviewers();
  const assign = useAssignReviewer();
  const unassign = useUnassignReviewer();
  const [target, setTarget] = useState<{ id: string; fullName: string } | null>(null);

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
        <h1 className="text-2xl md:text-[28px] font-bold text-text">Reviewer links</h1>
        <p className="text-sm text-text-light mt-1">Choose who reviews each healer's cases.</p>
      </div>

      {isLoading && <p className="text-text-light">Loading…</p>}
      {isError && <p className="text-red-500">Failed to load reviewer assignments.</p>}

      {!isLoading && !isError && (
        <div className="overflow-hidden rounded-2xl border border-border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-4">Healer</TableHead>
                <TableHead className="px-4">Reviewer</TableHead>
                <TableHead className="px-4">Approved cases</TableHead>
                <TableHead className="px-4">
                  <span className="sr-only">Actions</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((healer) => {
                const link = reviewerByHealer.get(healer.id);
                return (
                  <TableRow key={healer.id}>
                    <TableCell className="md:px-4 md:py-3">
                      <p className="font-medium text-text whitespace-normal">{healer.fullName}</p>
                      <p className="text-xs font-normal text-text-light break-all">{healer.email}</p>
                    </TableCell>
                    <TableCell className="md:px-4 whitespace-normal" data-label="Reviewer" data-testid={`reviewer-${healer.id}`}>
                      {link ? (link.reviewerName ?? "Unknown") : <span className="text-text-light">Not linked</span>}
                    </TableCell>
                    {/* The API counts approved cases only for linked healers. */}
                    <TableCell
                      className="md:px-4 tabular-nums"
                      data-label="Approved cases"
                      data-testid={link ? `approved-${healer.id}` : undefined}
                    >
                      {link ? (link.approvedCount ?? 0) : null}
                    </TableCell>
                    <TableCell className="md:px-4" data-label="">
                      <div className="flex w-full justify-end gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setTarget(healer)}
                          aria-label={link ? `Change reviewer for ${healer.fullName}` : `Assign a reviewer for ${healer.fullName}`}
                        >
                          {link ? "Change" : "Assign"}
                        </Button>
                        {link && (
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={unassign.isPending}
                            onClick={() => unassign.mutate(healer.id)}
                            aria-label={`Unlink instructor from ${healer.fullName}`}
                          >
                            Unlink
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
              {rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} className="p-4 text-text-light">No active healers.</TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {target && (
        <AssignReviewerDialog
          key={target.id}
          healer={target}
          candidates={eligible.data ?? []}
          currentReviewerId={reviewerByHealer.get(target.id)?.reviewerId}
          currentUserId={user?.id}
          pending={assign.isPending}
          onAssign={(reviewerId) =>
            assign.mutate({ healerId: target.id, reviewerId }, { onSuccess: () => setTarget(null) })
          }
          onClose={() => setTarget(null)}
        />
      )}
    </div>
  );
}
