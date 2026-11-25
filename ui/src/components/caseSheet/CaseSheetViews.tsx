import { useState } from "react";
import { InfoTip } from "@/components/InfoTip";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { type ChiefComplaintDetails, complaintLine, formatDuration } from "@/lib/chiefComplaints";

const TAG_TONE: Record<string, string> = {
  Underweight: "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-200",
  Normal: "bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-200",
  Overweight: "bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200",
  Obese: "bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200",
};

const sentence = (v?: string | null) => (v ? v.charAt(0) + v.slice(1).toLowerCase() : "");

/** The BMI figure with its tag chip; "Not provided" without both measurements. */
export function BmiValue({ bmi, category }: { bmi?: number | null; category?: string | null }) {
  if (bmi == null) return <span>Not provided</span>;
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <strong>{bmi}</strong>
      {category && (
        <span className={`inline-flex h-6 items-center rounded-full px-2.5 text-xs font-semibold ${TAG_TONE[category] ?? "bg-muted"}`}>
          {category}
        </span>
      )}
      <InfoTip
        label="About BMI"
        text="Worked out from height and weight. Underweight below 18.5, Normal 18.5 to 24.9, Overweight 25 to 29.9, Obese 30 and above."
      />
    </span>
  );
}

/** One "problem since 3 months" line per row; a row without a duration is tagged "Duration missing". */
export function ChiefComplaintsView({ complaints }: { complaints?: ChiefComplaintDetails[] | null }) {
  if (!complaints?.length) return <span>—</span>;
  return (
    <ul className="space-y-1.5">
      {complaints.map((c, i) => {
        const missing = !formatDuration(c.durationValue, c.durationUnit);
        return (
          <li key={c.id ?? i} className="flex flex-wrap items-center gap-2">
            {missing ? <span className="font-semibold">{c.problem}</span> : <span>{complaintLine(c)}</span>}
            {missing && (
              <span className="inline-flex h-6 items-center rounded-full bg-amber-100 px-2.5 text-xs font-semibold text-amber-800 dark:bg-amber-900/40 dark:text-amber-200">
                Duration missing
              </span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

const hasDetails = (c: ChiefComplaintDetails) =>
  !!(c.onset || c.location || c.characterText || c.aggravatingFactor || c.relievingFactor || c.timing || c.timingPattern);

/**
 * A "Click to view" link, like lab findings and medications, that opens the presenting-illness answers.
 * With nothing answered there is no link.
 */
export function PresentingIllnessView({ complaints }: { complaints?: ChiefComplaintDetails[] | null }) {
  const [open, setOpen] = useState(false);
  const list = (complaints ?? []).filter((c) => c.problem);
  if (!list.some(hasDetails)) return <span>—</span>;
  return (
    <>
      <Button
        type="button"
        variant="link"
        onClick={() => setOpen(true)}
        className="h-auto p-0 text-sm font-normal underline underline-offset-2 decoration-primary/40 hover:decoration-primary"
      >
        Click to view
      </Button>
      {open && (
        <Dialog open onOpenChange={(next) => !next && setOpen(false)}>
          <DialogContent className="flex max-h-[80vh] flex-col gap-0 overflow-hidden p-0 sm:max-w-lg">
            <div className="border-b border-border px-5 py-4">
              <DialogTitle className="text-sm font-semibold text-text-dark">Presenting illness</DialogTitle>
            </div>
            <div className="overflow-y-auto px-5 py-4">
              <PresentingIllnessBlocks list={list} />
            </div>
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

/** One block per problem; a problem with no answers says so. */
function PresentingIllnessBlocks({ list }: { list: ChiefComplaintDetails[] }) {
  return (
    <div className="space-y-3">
      {list.map((c, i) => {
        const duration = formatDuration(c.durationValue, c.durationUnit);
        const timing = c.timing === "PATTERN" && c.timingPattern ? `Pattern: ${c.timingPattern}` : sentence(c.timing);
        const rows: [string, string | null | undefined][] = [
          ["Onset", sentence(c.onset)],
          ["Location", c.location],
          ["Feels like", c.characterText],
          ["Worse with", c.aggravatingFactor],
          ["Better with", c.relievingFactor],
          ["Timing", timing],
        ];
        return (
          <div key={c.id ?? i} className="space-y-2 rounded-lg border border-border bg-muted/30 p-3">
            <div className="flex items-center justify-between gap-2">
              <strong>{c.problem}</strong>
              {duration && <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-semibold text-muted-foreground">{duration}</span>}
            </div>
            {hasDetails(c) ? (
              <dl className="grid grid-cols-[6rem_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm">
                {rows.filter(([, v]) => v).map(([label, v]) => (
                  <div key={label} className="contents">
                    <dt className="text-muted-foreground">{label}</dt>
                    <dd>{v}</dd>
                  </div>
                ))}
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">No details entered.</p>
            )}
          </div>
        );
      })}
    </div>
  );
}

/** The four patient histories, only those filled in. */
export function HistoriesView({ snapshot }: {
  snapshot?: { pastHistory?: string | null; surgicalHistory?: string | null; familyHistory?: string | null; psychosocialHistory?: string | null } | null;
}) {
  const rows: [string, string | null | undefined][] = [
    ["Past history", snapshot?.pastHistory],
    ["Surgical history", snapshot?.surgicalHistory],
    ["Family history", snapshot?.familyHistory],
    ["Psychosocial history", snapshot?.psychosocialHistory],
  ];
  const filled = rows.filter(([, v]) => v);
  if (filled.length === 0) return <span>—</span>;
  return (
    <dl className="grid grid-cols-[9rem_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-sm">
      {filled.map(([label, v]) => (
        <div key={label} className="contents">
          <dt className="text-muted-foreground">{label}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  );
}
