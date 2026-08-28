import { useEffect, useState } from "react";

import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { MultiSelectList } from "@/components/ui/multi-select/multi-select-list";
import type { AdminHealer } from "@/hooks/admin/useAdminHealers";

export interface UserPickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Full Healer list (D-06: useAdminHealers() / GET /api/v1/healers). */
  healers: AdminHealer[];
  /** Healer ids already assigned to this role — never offered. */
  alreadyAssigned: string[];
  /** Fires with the selected healer ids only. */
  onConfirm: (userIds: string[]) => void;
  loading?: boolean;
}

/**
 * Searchable Healer picker for role assignment (UI-SPEC Screen E).
 *
 * Option source is the caller's `healers` list — D-06: the existing
 * useAdminHealers() listing filtered client-side, no dedicated search endpoint.
 * Two client-side exclusions, both UX-only and NOT access-control boundaries:
 *   - every healer whose `role === "SUPADM"` (D-07: SUPADM is never a role
 *     target and always bypasses permission checks unconditionally), with an
 *     on-screen note so the exclusion is legible;
 *   - every healer whose id is in `alreadyAssigned`.
 * Search matches on name OR email: `getOptionLabel` returns both joined, so the
 * underlying cmdk filter sees the email even for a healer that has a name.
 * Selection resets to empty on every open.
 */
export function UserPickerDialog({
  open,
  onOpenChange,
  healers,
  alreadyAssigned,
  onConfirm,
  loading,
}: UserPickerDialogProps) {
  const [selected, setSelected] = useState<AdminHealer[]>([]);

  // Assignable healers: drop SUPADM accounts (D-07) and anyone already on the
  // role. Recomputed every render (and specifically re-evaluated whenever
  // `open` flips) so a user assigned in a previous round is not offered again.
  const options = healers.filter(
    (healer) => healer.role !== "SUPADM" && !alreadyAssigned.includes(healer.id),
  );

  useEffect(() => {
    if (open) setSelected([]);
  }, [open]);

  const handleConfirm = () => {
    onConfirm(selected.map((healer) => healer.id));
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add users</DialogTitle>
        </DialogHeader>

        <MultiSelectList<AdminHealer>
          options={options}
          value={selected}
          onChange={(next) => setSelected(next)}
          getOptionKey={(healer) => healer.id}
          getOptionLabel={(healer) =>
            [healer.fullName, healer.email].filter(Boolean).join(" ")
          }
          renderOption={(healer) => (
            <div className="flex flex-col">
              <span>{healer.fullName || healer.email}</span>
              {healer.fullName ? (
                <span className="text-xs text-text-light">{healer.email}</span>
              ) : null}
            </div>
          )}
          searchPlaceholder="Search by name or email"
          emptyText="No users left to assign."
        />

        <p className="text-xs text-text-light">
          Super admins already have full access and aren't listed.
        </p>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Keep current users
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={selected.length === 0 || loading}
          >
            Assign users
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
