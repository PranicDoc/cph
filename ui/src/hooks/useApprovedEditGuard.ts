import { createElement, useCallback, useMemo, useRef, useState, type ReactNode } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useCaseReviewStatuses } from "./useCaseReviewStatuses";

/** Shown before a healer edits a case whose review is approved. */
export const APPROVED_EDIT_MESSAGE =
  "This case is approved. Editing it will dismiss the approval and you will need a new review.";
export const APPROVED_EDIT_CONFIRM = "Edit and dismiss approval";

export interface ApprovedEditGuard {
  /** True while one of the cases has an approved review. */
  isApproved: boolean;
  /** Runs `edit` at once, or after the healer confirms the popup when the case is approved. */
  guard: (edit: () => void) => void;
  /** The confirmation popup; render it once on the page. */
  dialog: ReactNode;
}

/**
 * Asks before an edit that would dismiss an approved review. The server dismisses the approval on
 * the write itself; this only makes sure the healer knows first. Cancel edits nothing, so the
 * approval stands. Once confirmed, later edits on the same page go straight through. Pass every
 * case the edit changes (a patient's medical history changes all of their cases). When the status
 * cannot be read the edit is not blocked: the dismissal still happens on the server.
 */
export function useApprovedEditGuard(caseIds: string | string[] | null | undefined): ApprovedEditGuard {
  const ids = useMemo(
    () => (Array.isArray(caseIds) ? caseIds : caseIds ? [caseIds] : []).filter(Boolean),
    // A new array with the same ids must not refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [Array.isArray(caseIds) ? caseIds.join(",") : caseIds]
  );
  const statuses = useCaseReviewStatuses(ids, { scope: "edit-guard", retry: false });
  const isApproved = ids.some((id) => statuses.data?.[id]?.status === "APPROVED");

  const [pending, setPending] = useState<(() => void) | null>(null);
  const confirmed = useRef(false);

  const guard = useCallback(
    (edit: () => void) => {
      if (!isApproved || confirmed.current) {
        edit();
        return;
      }
      setPending(() => edit);
    },
    [isApproved]
  );

  const dialog = createElement(ConfirmDialog, {
    open: pending !== null,
    onOpenChange: (open: boolean) => {
      if (!open) setPending(null);
    },
    title: "Edit an approved case?",
    description: APPROVED_EDIT_MESSAGE,
    confirmLabel: APPROVED_EDIT_CONFIRM,
    cancelLabel: "Cancel",
    onConfirm: () => {
      const edit = pending;
      confirmed.current = true;
      setPending(null);
      edit?.();
    },
  });

  return { isApproved, guard, dialog };
}
