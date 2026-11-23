import { useState } from "react";
import { Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TEXT_LIMITS } from "@/lib/textLimits";
import {
  type ChiefComplaintRow,
  DURATION_UNITS,
  MAX_CHIEF_COMPLAINTS,
  MAX_DURATION,
  durationError,
  emptyComplaintRow,
  isFilledRow,
  periodError,
} from "@/lib/chiefComplaints";

const ROW_GRID = "md:grid-cols-[minmax(0,1fr)_7.5rem_9.5rem_2.5rem]";

/**
 * The chief complaint as a table: problem, duration, period. A blank problem is skipped on save; a problem
 * needs both a duration and a period. Up to 10 rows. On a phone each row stacks into a card.
 */
export function ChiefComplaintsField({
  value,
  onChange,
  readOnly,
}: {
  value: ChiefComplaintRow[];
  onChange: (rows: ChiefComplaintRow[]) => void;
  readOnly?: boolean;
}) {
  const rows = value?.length ? value : [emptyComplaintRow()];
  // A row's problems are flagged once the person has left it, not while they are still typing it.
  const [visited, setVisited] = useState<Set<number>>(new Set());
  const markVisited = (i: number) => setVisited((s) => (s.has(i) ? s : new Set(s).add(i)));

  const update = (i: number, patch: Partial<ChiefComplaintRow>) =>
    onChange(rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const remove = (i: number) => {
    const next = rows.filter((_, idx) => idx !== i);
    onChange(next.length ? next : [emptyComplaintRow()]);
    setVisited(new Set());
  };
  const atLimit = rows.length >= MAX_CHIEF_COMPLAINTS;

  return (
    <div className="space-y-3">
      <div className="rounded-lg border border-border">
        <div className={`hidden gap-3 border-b border-border bg-muted/50 px-3 py-2 text-xs font-semibold text-muted-foreground md:grid ${ROW_GRID}`}>
          <span>Problem</span>
          <span>Duration</span>
          <span>Period</span>
          <span />
        </div>
        {rows.map((row, i) => {
          const showErrors = visited.has(i);
          const dErr = showErrors ? durationError(row) : undefined;
          const pErr = showErrors ? periodError(row) : undefined;
          const missingDuration = row.legacy && isFilledRow(row) && (durationError(row) || periodError(row));
          return (
            <div
              key={row.id ?? i}
              className={`grid grid-cols-1 gap-3 border-b border-border p-3 last:border-b-0 ${ROW_GRID} ${missingDuration ? "bg-amber-50 dark:bg-amber-950/20" : ""}`}
            >
              <div className="space-y-1.5">
                <Label className="text-xs md:sr-only" htmlFor={`cc-problem-${i}`}>Problem {i + 1}</Label>
                <Input
                  id={`cc-problem-${i}`}
                  value={row.problem}
                  maxLength={TEXT_LIMITS.shortText}
                  disabled={readOnly}
                  placeholder={i === 0 ? "E.g. Stomachache" : "Add another problem"}
                  onChange={(e) => update(i, { problem: e.target.value })}
                  onBlur={() => markVisited(i)}
                />
                {missingDuration && (
                  <span className="inline-flex h-6 items-center rounded-full bg-amber-100 px-2.5 text-xs font-semibold text-amber-800 dark:bg-amber-900/40 dark:text-amber-200">
                    Duration missing
                  </span>
                )}
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs md:sr-only" htmlFor={`cc-duration-${i}`}>Duration {i + 1}</Label>
                <Input
                  id={`cc-duration-${i}`}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={MAX_DURATION}
                  step={1}
                  value={row.durationValue ?? ""}
                  disabled={readOnly}
                  aria-invalid={!!dErr || undefined}
                  onChange={(e) => update(i, { durationValue: e.target.value })}
                  onBlur={() => markVisited(i)}
                />
                {dErr && <p className="text-xs text-danger">{dErr}</p>}
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs md:sr-only" htmlFor={`cc-period-${i}`}>Period {i + 1}</Label>
                <Select
                  value={row.durationUnit || ""}
                  disabled={readOnly}
                  onValueChange={(v) => { update(i, { durationUnit: v as ChiefComplaintRow["durationUnit"] }); markVisited(i); }}
                >
                  <SelectTrigger id={`cc-period-${i}`} className="w-full" aria-invalid={!!pErr || undefined}>
                    <SelectValue placeholder="Select" />
                  </SelectTrigger>
                  <SelectContent>
                    {DURATION_UNITS.map((u) => (
                      <SelectItem key={u.value} value={u.value}>{u.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {pErr && <p className="text-xs text-danger">{pErr}</p>}
              </div>
              {!readOnly && (
                <div className="flex items-start md:pt-0">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Remove problem ${i + 1}`}
                    onClick={() => remove(i)}
                    className="text-danger"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </div>
          );
        })}
      </div>
      {!readOnly && (
        <div className="flex items-center gap-3">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={atLimit}
            onClick={() => onChange([...rows, emptyComplaintRow()])}
          >
            <Plus className="h-4 w-4" /> Add problem
          </Button>
          {atLimit && <span className="text-xs text-muted-foreground">You can add up to {MAX_CHIEF_COMPLAINTS} problems</span>}
        </div>
      )}
    </div>
  );
}
