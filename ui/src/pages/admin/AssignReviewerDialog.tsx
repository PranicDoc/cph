import { useState } from "react";
import { Info } from "lucide-react";

import type { HealerOption } from "../../hooks/admin/useReviewerAssignments";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

interface AssignReviewerDialogProps {
  /** The healer whose cases are to be reviewed. */
  healer: { id: string; fullName: string };
  /** Every active account, as the server lists them. */
  candidates: HealerOption[];
  currentReviewerId?: string;
  currentUserId?: string;
  /** True while the accounts are still on their way. */
  loading?: boolean;
  /** What else this assignment changes, when the opening screen needs to say so. */
  note?: string;
  pending: boolean;
  onAssign: (reviewerId: string) => void;
  onClose: () => void;
}

function initials(name: string): string {
  return name.split(" ").map((part) => part[0]).join("").toUpperCase().slice(0, 2);
}

function CandidatePill({ candidate }: { candidate: HealerOption }) {
  if (candidate.superAdmin) {
    return <Badge className="text-[var(--hl-accent-text,var(--color-primary))]">Super admin</Badge>;
  }
  return candidate.hasReviewPermission
    ? <Badge variant="success">Has Review permission</Badge>
    : <Badge variant="warning">No Review permission</Badge>;
}

/**
 * Picks one reviewer for a healer from all active accounts. The healer themself is offered only
 * when they are a super admin, the one case in which the server accepts a self-assignment.
 * Mount it only while it is open: search and selection start fresh each time.
 */
export function AssignReviewerDialog({
  healer, candidates, currentReviewerId, currentUserId, loading, note, pending, onAssign, onClose,
}: AssignReviewerDialogProps) {
  // Whatever had focus before the dialog took it. There is no Dialog trigger for Radix to return to.
  const [opener] = useState(() => document.activeElement as HTMLElement | null);
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState(currentReviewerId);

  const offered = candidates.filter((c) => c.id !== healer.id || c.superAdmin);
  const term = search.trim().toLowerCase();
  const shown = offered.filter(
    (c) => c.fullName.toLowerCase().includes(term) || c.email.toLowerCase().includes(term)
  );
  const selected = offered.find((c) => c.id === selectedId);

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        className="flex max-h-[90dvh] flex-col gap-3.5 sm:max-w-[560px]"
        onCloseAutoFocus={(e) => {
          e.preventDefault();
          opener?.focus();
        }}
      >
        <DialogHeader className="pr-8">
          <DialogTitle className="text-xl font-semibold leading-tight">
            Assign a reviewer for {healer.fullName}
          </DialogTitle>
          <DialogDescription>
            Anyone can be chosen. The list is not limited to accounts with the Review permission.
          </DialogDescription>
        </DialogHeader>

        <Input
          type="search"
          aria-label="Search by name or email"
          placeholder="Search by name or email"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="shrink-0"
        />

        <div
          role="radiogroup"
          aria-label="Reviewer"
          className="min-h-24 flex-1 divide-y divide-border overflow-y-auto rounded-xl border border-border"
        >
          {shown.map((c) => (
            <label
              key={c.id}
              className={cn(
                "flex cursor-pointer items-center gap-3 px-3.5 py-2.5",
                c.id === selectedId && "bg-primary/10"
              )}
            >
              <input
                type="radio"
                name="reviewer"
                value={c.id}
                checked={c.id === selectedId}
                onChange={() => setSelectedId(c.id)}
                className="size-[18px] shrink-0 accent-primary"
              />
              <Avatar className="max-sm:hidden" aria-hidden="true">
                <AvatarFallback className="bg-primary/10 text-xs font-semibold">
                  {initials(c.fullName)}
                </AvatarFallback>
              </Avatar>
              {/* On phones the pill drops under the name so neither is squeezed. */}
              <span className="flex min-w-0 flex-1 flex-col items-start gap-1 sm:flex-row sm:items-center sm:gap-3">
                <span className="w-full min-w-0 sm:flex-1">
                  <span className="block truncate font-medium text-text">
                    {c.fullName}{c.id === currentUserId && " (you)"}
                  </span>
                  <span className="block truncate text-xs text-text-light">{c.email}</span>
                </span>
                <CandidatePill candidate={c} />
              </span>
            </label>
          ))}
          {shown.length === 0 && (
            <p className="p-3.5 text-text-light">
              {loading ? "Loading accounts…" : "No accounts match your search."}
            </p>
          )}
        </div>

        <div className="flex shrink-0 gap-2.5 rounded-xl border border-primary/30 bg-primary/10 px-3 py-2.5 text-[13px] leading-snug text-text">
          <Info className="mt-0.5 size-[18px] shrink-0" aria-hidden="true" />
          <div className="space-y-1">
            {note && <p className="font-semibold">{note}</p>}
            <p>
              Someone without the Review permission will see the Reviews screen for the healers
              assigned to them, and nothing else.
            </p>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={pending || !selected || selected.id === currentReviewerId}
            onClick={() => selected && onAssign(selected.id)}
            className="min-w-0"
          >
            <span className="truncate">{selected ? `Assign ${selected.fullName}` : "Assign"}</span>
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
