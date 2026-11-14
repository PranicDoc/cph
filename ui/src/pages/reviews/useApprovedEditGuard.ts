import { createElement, useCallback, useMemo, useRef, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { CASE_STATUSES_KEY } from "../../lib/reviewQueryKeys";
import { APPROVED_EDIT_CONFIRM, APPROVED_EDIT_MESSAGE, caseStatuses } from "./reviewApi";

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
  // Under the case-statuses key, so a live STATUS_CHANGED event refetches it.
  const statuses = useQuery({
    queryKey: [...CASE_STATUSES_KEY, "edit-guard", ids],
    queryFn: () => caseStatuses(ids),
    enabled: ids.length > 0,
    retry: false,
  });
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
