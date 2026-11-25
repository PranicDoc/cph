import { Fragment, type ReactNode } from "react";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DynamicFormEngine, type FormSchema } from "@/components/DynamicFormEngine";
import { cn } from "@/lib/utils";
import { CASE_SHEET_STEPS, LAST_STEP, PatternCheckContext } from "./useCaseSheetSteps";

/** The step names as chips on a laptop, and as "Step 1 of 3" on a phone. */
function StepHeader({ step, onSelect }: { step: number; onSelect: (step: number) => void }) {
  const next = CASE_SHEET_STEPS[step + 1];
  return (
    <nav aria-label="Case details steps">
      <p className="text-sm text-muted-foreground md:hidden">
        Step {step + 1} of {CASE_SHEET_STEPS.length}
        {next ? ` · Next: ${next.toLowerCase()}` : ""}
      </p>
      <ol className="hidden items-center gap-3 md:flex">
        {CASE_SHEET_STEPS.map((title, i) => (
          <Fragment key={title}>
            {i > 0 && <li aria-hidden="true" className="h-0.5 min-w-5 flex-1 rounded-full bg-border" />}
            <li>
              <button
                type="button"
                onClick={() => onSelect(i)}
                aria-current={i === step ? "step" : undefined}
                className={cn(
                  "flex items-center gap-2.5 text-sm font-medium",
                  i === step ? "text-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                <span
                  className={cn(
                    "grid h-7 w-7 place-items-center rounded-full text-xs font-semibold",
                    i === step ? "bg-primary text-primary-foreground ring-4 ring-primary/20" : "bg-muted",
                  )}
                >
                  {i < step ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : i + 1}
                </span>
                {title}
              </button>
            </li>
          </Fragment>
        ))}
      </ol>
    </nav>
  );
}

/**
 * The case sheet form in three steps: chief complaints, presenting illness, then everything else. The schema's
 * first two groups are the first two steps. All steps stay mounted (only one is shown) so that saving from the
 * last step still validates every field and nothing typed is lost on the way back.
 */
export function CaseSheetSteps({
  form,
  schema,
  step,
  onSelect,
  patternCheck,
}: {
  form: unknown;
  schema: FormSchema;
  step: number;
  onSelect: (step: number) => void;
  patternCheck: number;
}) {
  const panels = [schema.slice(0, 1), schema.slice(1, 2), schema.slice(2)];
  return (
    <PatternCheckContext.Provider value={patternCheck}>
      <StepHeader step={step} onSelect={onSelect} />
      {panels.map((panel, i) => (
        <div key={CASE_SHEET_STEPS[i]} hidden={i !== step}>
          <DynamicFormEngine form={form} schema={panel} />
        </div>
      ))}
    </PatternCheckContext.Provider>
  );
}

/** Back on the left of the forward button; the form's own submit button appears only on the last step. */
export function CaseSheetStepButtons({
  step,
  onSelect,
  submit,
}: {
  step: number;
  onSelect: (step: number) => void;
  submit: ReactNode;
}) {
  return (
    <div className="flex w-full flex-col-reverse gap-3 sm:w-auto sm:flex-row">
      {step > 0 && (
        <Button type="button" variant="outline" onClick={() => onSelect(step - 1)} className="h-auto px-6 py-2.5">
          Back to {CASE_SHEET_STEPS[step - 1].toLowerCase()}
        </Button>
      )}
      {/* Keys keep Next and Save as separate elements: a reused button would submit on the click that reveals Save. */}
      {step < LAST_STEP ? (
        <Button key="next" type="button" onClick={() => onSelect(step + 1)} className="h-auto px-6 py-2.5">
          Next: {CASE_SHEET_STEPS[step + 1].toLowerCase()}
        </Button>
      ) : (
        <Fragment key="submit">{submit}</Fragment>
      )}
    </div>
  );
}
