import { useState } from "react";
import { InfoTip } from "@/components/InfoTip";
import { CharacterCount } from "@/components/form/CharacterCount";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { TEXT_LIMITS } from "@/lib/textLimits";
import { cn } from "@/lib/utils";
import {
  type ChiefComplaintRow,
  type Onset,
  type ProblemHistory,
  type Timing,
  formatDuration,
  hasProblemHistory,
  isCompleteRow,
} from "@/lib/chiefComplaints";

const ONSETS: { value: Onset; label: string }[] = [
  { value: "SUDDEN", label: "Sudden" },
  { value: "GRADUAL", label: "Gradual" },
];
const TIMINGS: { value: Timing; label: string }[] = [
  { value: "CONSTANT", label: "Constant" },
  { value: "INTERMITTENT", label: "Intermittent" },
  { value: "PATTERN", label: "Pattern" },
];

function Radios<T extends string>({
  legend, options, value, onChange, disabled,
}: {
  legend: string;
  options: { value: T; label: string }[];
  value: T | null | undefined;
  onChange: (v: T | null) => void;
  disabled?: boolean;
}) {
  return (
    <div role="radiogroup" aria-label={legend} className="flex flex-wrap gap-2">
      {options.map((o) => {
        const on = value === o.value;
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={on}
            disabled={disabled}
            // Choosing the selected one again clears it: every answer here is optional.
            onClick={() => onChange(on ? null : o.value)}
            className={cn(
              "inline-flex h-11 items-center gap-2 rounded-md border px-3.5 text-sm font-medium transition-colors",
              on ? "border-primary bg-primary/10" : "border-border bg-card hover:bg-muted/50",
            )}
          >
            <span className={cn("h-4 w-4 rounded-full border-2", on ? "border-primary bg-primary shadow-[inset_0_0_0_3px_var(--card)]" : "border-muted-foreground")} />
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

function LabelWithTip({ htmlFor, children, tip }: { htmlFor?: string; children: React.ReactNode; tip: string }) {
  return (
    <div className="flex items-center gap-1.5">
      <Label htmlFor={htmlFor} className="text-sm font-medium">{children}</Label>
      <InfoTip text={tip} label={`About ${String(children).toLowerCase()}`} />
    </div>
  );
}

/**
 * The history of presenting illness, one problem at a time: pick the problem in the dropdown, then answer
 * onset, location, character, what makes it worse or better, and timing. Everything is optional. The problems
 * and their durations come from the chief complaint table above; only complete rows appear.
 */
export function PresentingIllnessField({
  value,
  onChange,
  readOnly,
}: {
  value: ChiefComplaintRow[];
  onChange: (rows: ChiefComplaintRow[]) => void;
  readOnly?: boolean;
}) {
  const rows = value ?? [];
  // Index in `rows` of each problem that can be described (problem, duration and period filled in).
  const eligible = rows.map((r, i) => (isCompleteRow(r) ? i : -1)).filter((i) => i >= 0);
  const [selected, setSelected] = useState(0);

  if (eligible.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground">
        Add a chief complaint with how long you have had it, and you can describe it here.
      </p>
    );
  }

  const position = Math.min(selected, eligible.length - 1);
  const rowIndex = eligible[position];
  const row = rows[rowIndex];
  const history: ProblemHistory = row.problemHistory ?? {};
  const filledCount = eligible.filter((i) => hasProblemHistory(rows[i].problemHistory)).length;

  const set = (patch: Partial<ProblemHistory>) =>
    onChange(rows.map((r, i) => (i === rowIndex ? { ...r, problemHistory: { ...(r.problemHistory ?? {}), ...patch } } : r)));

  const textArea = (
    id: string, field: "characterText" | "aggravatingFactor" | "relievingFactor" | "timingPattern",
    opts: { rows?: number; placeholder?: string } = {},
  ) => (
    <>
      <Textarea
        id={id}
        rows={opts.rows ?? 3}
        value={history[field] ?? ""}
        maxLength={TEXT_LIMITS.presentingIllness}
        disabled={readOnly}
        placeholder={opts.placeholder}
        onChange={(e) => set({ [field]: e.target.value })}
        className="p-3"
      />
      <CharacterCount value={history[field]} max={TEXT_LIMITS.presentingIllness} />
    </>
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="w-full space-y-1.5 sm:max-w-md">
          <LabelWithTip htmlFor="pi-problem" tip="These are the problems from the chief complaints. Pick one, then answer the questions below. All answers are optional.">
            Choose a problem
          </LabelWithTip>
          <Select value={String(position)} onValueChange={(v) => setSelected(Number(v))}>
            <SelectTrigger id="pi-problem" className="w-full font-semibold">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {eligible.map((ri, n) => (
                <SelectItem key={rows[ri].id ?? ri} value={String(n)}>
                  {n + 1}. {rows[ri].problem.trim()}{hasProblemHistory(rows[ri].problemHistory) ? " (filled)" : ""}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <p className="text-sm text-muted-foreground sm:pb-2.5">
          Problem {position + 1} of {eligible.length} · {filledCount} filled
        </p>
      </div>

      <div className="space-y-5 rounded-lg border border-border p-4">
        <div className="flex flex-wrap items-center gap-3">
          <h4 className="text-base font-semibold">{row.problem.trim()}</h4>
          <span className="inline-flex h-6 items-center rounded-full bg-muted px-2.5 text-xs font-semibold text-muted-foreground">
            Since {formatDuration(row.durationValue, row.durationUnit)}
          </span>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          <div className="space-y-4">
            <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">Where and how it started</p>
            <div className="space-y-1.5">
              <LabelWithTip tip="Sudden: it started within hours or a day. Gradual: it built up slowly over days or weeks.">Onset</LabelWithTip>
              <Radios legend="Onset" options={ONSETS} value={history.onset} onChange={(v) => set({ onset: v })} disabled={readOnly} />
            </div>
            <div className="space-y-1.5">
              <LabelWithTip htmlFor="pi-location" tip='Which part of the body the problem is in, for example "right knee" or "lower back".'>Location</LabelWithTip>
              <Input
                id="pi-location"
                value={history.location ?? ""}
                maxLength={TEXT_LIMITS.shortText}
                disabled={readOnly}
                onChange={(e) => set({ location: e.target.value })}
              />
            </div>
          </div>

          <div className="space-y-4">
            <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">What it feels like</p>
            <div className="space-y-1.5">
              <LabelWithTip htmlFor="pi-character" tip="Describe the problem in your own words: sharp, dull, burning, throbbing, heavy and so on.">Character</LabelWithTip>
              {textArea("pi-character", "characterText", { rows: 5 })}
            </div>
          </div>

          <div className="space-y-4">
            <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">What changes it</p>
            <div className="space-y-1.5">
              <LabelWithTip htmlFor="pi-worse" tip="Anything that makes the problem stronger, such as movement, food, weather or stress.">Makes it worse</LabelWithTip>
              {textArea("pi-worse", "aggravatingFactor", { rows: 2 })}
            </div>
            <div className="space-y-1.5">
              <LabelWithTip htmlFor="pi-better" tip="Anything that eases the problem, such as rest, warmth, a medicine or a position.">Makes it better</LabelWithTip>
              {textArea("pi-better", "relievingFactor", { rows: 2 })}
            </div>
          </div>
        </div>

        <hr className="border-border" />
        <div className="grid grid-cols-1 items-start gap-6 md:grid-cols-2">
          <div className="space-y-1.5">
            <LabelWithTip tip="Constant: always there. Intermittent: comes and goes. Pattern: happens at set times, which you can describe.">Timing</LabelWithTip>
            <Radios legend="Timing" options={TIMINGS} value={history.timing} onChange={(v) => set({ timing: v })} disabled={readOnly} />
          </div>
          {history.timing === "PATTERN" && (
            <div className="space-y-1.5">
              <Label htmlFor="pi-pattern" className="text-sm font-medium">Describe the pattern</Label>
              {textArea("pi-pattern", "timingPattern", { placeholder: "For example: every morning, or only on weekdays" })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
