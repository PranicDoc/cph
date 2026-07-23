import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import {
  ENERGY_LEVEL_LABELS,
  ACTIVATION_LEVEL_LABELS,
  type ChakraCatalogEntry,
  type EnergyLevel,
  type ActivationLevel,
} from "@/components/ChakraScan/types/spatial-chakra";
import type { UnresolvedSegment } from "@/lib/chakraNlp";

const MANUAL = "__manual__";

const ENERGY_OPTIONS = Object.entries(ENERGY_LEVEL_LABELS) as Array<
  [EnergyLevel, string]
>;
const ACTIVATION_OPTIONS = Object.entries(ACTIVATION_LEVEL_LABELS) as Array<
  [ActivationLevel, string]
>;

/** One resolution per unresolved segment, in the same order. */
export type SegmentResolution =
  | { type: "chakra"; chakra: ChakraCatalogEntry }
  | { type: "levels"; energyLevel?: EnergyLevel; activationLevel?: ActivationLevel }
  | { type: "skip" };

interface SegmentSelection {
  chakraId?: string;
  energyLevel?: EnergyLevel;
  activationLevel?: ActivationLevel;
  skipped?: boolean;
}

interface TranscriptionReviewModalProps {
  open: boolean;
  status: "processing" | "review";
  unresolved: UnresolvedSegment[];
  /** Full catalog — lets the manual "not listed" dropdown offer every
   * chakra, not just the top-ranked guesses in segment.candidates. */
  availableChakras: ChakraCatalogEntry[];
  onConfirm: (resolutions: SegmentResolution[]) => void;
}

/**
 * Two states of one modal rather than two components: "processing" (fully
 * blocking, no close affordance — matches the recording pipeline actually
 * running) and "review" (per-phrase confirmation once it's done). Nothing
 * is pre-selected by default, including the top-ranked "best match" — the
 * whole point of this screen is a real human decision, not a rubber-stamp.
 */
export function TranscriptionReviewModal({
  open,
  status,
  unresolved,
  availableChakras,
  onConfirm,
}: TranscriptionReviewModalProps) {
  const [selections, setSelections] = useState<Record<number, SegmentSelection>>({});

  function isDone(segment: UnresolvedSegment, index: number): boolean {
    const sel = selections[index];
    if (sel?.skipped) return true;
    if (segment.knownChakra) {
      const hasEnergy = segment.detectedEnergy !== null || sel?.energyLevel !== undefined;
      const hasActivation =
        segment.detectedActivation !== null || sel?.activationLevel !== undefined;
      return hasEnergy && hasActivation;
    }
    return sel?.chakraId !== undefined;
  }

  const confirmedCount = unresolved.filter((s, i) => isDone(s, i)).length;
  const allResolved = unresolved.length > 0 && confirmedCount === unresolved.length;

  function update(index: number, patch: Partial<SegmentSelection>) {
    setSelections((prev) => ({ ...prev, [index]: { ...prev[index], ...patch } }));
  }

  function handleConfirm() {
    const resolutions: SegmentResolution[] = unresolved.map((segment, index) => {
      const sel = selections[index];
      if (sel?.skipped) return { type: "skip" };

      if (segment.knownChakra) {
        return {
          type: "levels",
          energyLevel: segment.detectedEnergy ?? sel?.energyLevel,
          activationLevel: segment.detectedActivation ?? sel?.activationLevel,
        };
      }

      if (!sel?.chakraId) return { type: "skip" };
      const chakra = availableChakras.find((c) => c.id === sel.chakraId);
      return chakra ? { type: "chakra", chakra } : { type: "skip" };
    });
    onConfirm(resolutions);
    setSelections({});
  }

  return (
    <Dialog open={open}>
      <DialogContent
        showCloseButton={false}
        onEscapeKeyDown={(e) => e.preventDefault()}
        onPointerDownOutside={(e) => e.preventDefault()}
        onInteractOutside={(e) => e.preventDefault()}
        className={cn(
          status === "review" && "flex max-h-[85vh] flex-col gap-0 p-0 sm:max-w-lg"
        )}
      >
        {status === "processing" ? (
          <div className="flex flex-col items-center gap-4 py-6 text-center">
            <Spinner size="lg" />
            <div className="space-y-1.5">
              <DialogTitle>Processing your recording&hellip;</DialogTitle>
              <DialogDescription>
                Matching what was said against your chakra scan vocabulary.
                This takes a few seconds.
              </DialogDescription>
            </div>
            <Badge
              variant="warning"
              className="h-auto whitespace-normal px-3 py-1.5 text-center leading-snug"
            >
              Don&rsquo;t refresh or close this page
            </Badge>
          </div>
        ) : (
          <>
            <DialogHeader className="border-b border-border px-4 py-4 sm:px-6">
              <DialogTitle>
                {unresolved.length === 1
                  ? "One phrase needs a quick confirmation"
                  : `${unresolved.length} phrases need a quick confirmation`}
              </DialogTitle>
              <DialogDescription>
                Most of the recording matched automatically. Pick the right
                chakra for what&rsquo;s left.
              </DialogDescription>
            </DialogHeader>

            <div className="flex-1 space-y-4 overflow-y-auto px-4 py-4 sm:px-6">
              {unresolved.map((segment, index) => {
                const sel = selections[index];
                const done = isDone(segment, index);
                const contextLabel = [
                  segment.detectedEnergy && ENERGY_LEVEL_LABELS[segment.detectedEnergy],
                  segment.detectedActivation &&
                    ACTIVATION_LEVEL_LABELS[segment.detectedActivation],
                ]
                  .filter(Boolean)
                  .join(", ");

                return (
                  <div
                    key={index}
                    className={cn(
                      "rounded-xl border p-4",
                      done ? "border-success/40" : "border-border"
                    )}
                  >
                    <div className="mb-2 flex items-center justify-between">
                      <span className="font-mono text-xs text-muted-foreground">
                        Phrase {index + 1} of {unresolved.length}
                      </span>
                      <Badge variant={done ? "success" : "warning"}>
                        {done ? "Confirmed" : "Needs review"}
                      </Badge>
                    </div>

                    <div className="mb-3 rounded-lg bg-muted px-3 py-2">
                      <p className="font-mono text-sm text-muted-foreground">
                        &ldquo;{segment.rawText}&rdquo;
                        {segment.knownChakra && ` — ${segment.knownChakra.name}`}
                      </p>
                      {contextLabel && (
                        <p className="mt-1 text-xs text-success">
                          Already matched: {contextLabel}
                        </p>
                      )}
                    </div>

                    {segment.knownChakra ? (
                      <div className="space-y-3">
                        {segment.detectedEnergy === null && (
                          <div>
                            <p className="mb-2 text-sm text-muted-foreground">
                              What was the energy level?
                            </p>
                            <div className="flex flex-wrap gap-2">
                              {ENERGY_OPTIONS.map(([level, label]) => (
                                <button
                                  key={level}
                                  type="button"
                                  onClick={() => update(index, { energyLevel: level, skipped: false })}
                                  className={cn(
                                    "rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
                                    sel?.energyLevel === level
                                      ? "border-primary bg-primary/10 text-primary"
                                      : "border-border bg-card hover:bg-muted"
                                  )}
                                >
                                  {label}
                                </button>
                              ))}
                            </div>
                          </div>
                        )}
                        {segment.detectedActivation === null && (
                          <div>
                            <p className="mb-2 text-sm text-muted-foreground">
                              What was the activation level?
                            </p>
                            <div className="flex flex-wrap gap-2">
                              {ACTIVATION_OPTIONS.map(([level, label]) => (
                                <button
                                  key={level}
                                  type="button"
                                  onClick={() => update(index, { activationLevel: level, skipped: false })}
                                  className={cn(
                                    "rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
                                    sel?.activationLevel === level
                                      ? "border-primary bg-primary/10 text-primary"
                                      : "border-border bg-card hover:bg-muted"
                                  )}
                                >
                                  {label}
                                </button>
                              ))}
                            </div>
                          </div>
                        )}
                        <button
                          type="button"
                          onClick={() => update(index, { skipped: true })}
                          className={cn(
                            "rounded-full border border-dashed px-3 py-1.5 text-sm font-medium transition-colors",
                            sel?.skipped
                              ? "border-muted-foreground text-foreground"
                              : "border-border text-muted-foreground hover:bg-muted"
                          )}
                        >
                          Leave incomplete for now
                        </button>
                      </div>
                    ) : (
                      <>
                        <p className="mb-2 text-sm text-muted-foreground">
                          Which chakra was this?
                        </p>
                        <div className="flex flex-wrap gap-2">
                          {segment.candidates.map((candidate, ci) => (
                            <button
                              key={candidate.id}
                              type="button"
                              onClick={() =>
                                update(index, { chakraId: candidate.id, skipped: false })
                              }
                              className={cn(
                                "flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
                                sel?.chakraId === candidate.id
                                  ? "border-primary bg-primary/10 text-primary"
                                  : "border-border bg-card hover:bg-muted"
                              )}
                            >
                              {ci === 0 && (
                                <span className="rounded bg-muted px-1 py-0.5 font-mono text-[10px] text-muted-foreground">
                                  best match
                                </span>
                              )}
                              {candidate.name}
                            </button>
                          ))}
                          <button
                            type="button"
                            onClick={() => update(index, { skipped: true, chakraId: undefined })}
                            className={cn(
                              "rounded-full border border-dashed px-3 py-1.5 text-sm font-medium transition-colors",
                              sel?.skipped
                                ? "border-muted-foreground text-foreground"
                                : "border-border text-muted-foreground hover:bg-muted"
                            )}
                          >
                            Skip
                          </button>
                        </div>

                        <div className="mt-2">
                          <select
                            value={
                              sel?.chakraId &&
                              !segment.candidates.some((c) => c.id === sel.chakraId)
                                ? sel.chakraId
                                : MANUAL
                            }
                            onChange={(e) => {
                              if (e.target.value === MANUAL) return;
                              update(index, { chakraId: e.target.value, skipped: false });
                            }}
                            className="w-full rounded-lg border border-border bg-card px-3 py-1.5 text-sm text-muted-foreground"
                          >
                            <option value={MANUAL}>Not listed — choose manually&hellip;</option>
                            {availableChakras.map((chakra) => (
                              <option key={chakra.id} value={chakra.id}>
                                {chakra.name}
                              </option>
                            ))}
                          </select>
                        </div>
                      </>
                    )}
                  </div>
                );
              })}
            </div>

            <DialogFooter className="border-t border-border px-4 py-4 sm:justify-between sm:px-6">
              <span className="text-center text-sm text-muted-foreground sm:self-center sm:text-left">
                <strong className="font-medium text-foreground">
                  {confirmedCount}
                </strong>{" "}
                of {unresolved.length} confirmed
              </span>
              <Button
                disabled={!allResolved}
                onClick={handleConfirm}
                className="w-full sm:w-auto"
              >
                Confirm &amp; Save Scan
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
