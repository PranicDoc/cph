import { useEffect, useRef } from "react";
import type { HPUTable } from "@/interfaces";
import type { useProtocolColors } from "@/hooks/useProtocolUnderstanding";
import type { UsualPick, UsualPickCategory } from "@/hooks/useUsualPicks";
import { Textarea } from "@/components/ui/textarea";
import { CharacterCount } from "@/components/form/CharacterCount";
import { TEXT_LIMITS } from "@/lib/textLimits";
import ColourStepCard from "./ColourStepCard";

interface Props {
  row: HPUTable;
  colors: ReturnType<typeof useProtocolColors>;
  picksFor: (chakra: string, category: UsualPickCategory, catalog: string[]) => UsualPick[];
  onChange: <K extends keyof HPUTable>(field: K, value: HPUTable[K]) => void;
}

export const REASON_REQUIRED = "Add why you worked on this chakra";

/** One chakra on the close-case page: why the healer worked on it, then 1. Cleanse and 2. Energise. */
export default function ChakraDetail({ row, colors, picksFor, onChange, showError = false }: Props & { showError?: boolean }) {
  const id = `reason-${row.chakra.replace(/\s+/g, "-")}`;
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    if (showError) reasonRef.current?.focus();
  }, [showError]);

  return (
    <div className="flex flex-col gap-[18px]">
      <div className="flex flex-col gap-1.5">
        <label className="hl-step-q" htmlFor={id}>
          Why did you work on this chakra? <span className="text-danger" aria-hidden="true">*</span>
        </label>
        <Textarea
          ref={reasonRef}
          id={id}
          rows={2}
          className="resize-none hl-reason"
          value={row.reason ?? ""}
          onChange={(e) => onChange("reason", e.target.value)}
          maxLength={TEXT_LIMITS.chakraReason}
          required
          aria-invalid={showError || undefined}
          aria-describedby={showError ? `${id}-err` : undefined}
        />
        <div className="flex items-start justify-between gap-3">
          {showError ? (
            <p id={`${id}-err`} role="alert" className="hl-field-err">
              {REASON_REQUIRED}
            </p>
          ) : (
            <span />
          )}
          <CharacterCount value={row.reason} max={TEXT_LIMITS.chakraReason} />
        </div>
      </div>
      <div className="hl-steps">
        <ColourStepCard
          title="1. Cleanse"
          kind="cleansing"
          chakra={row.chakra}
          picks={picksFor(row.chakra, "cleansing", colors.cleansingColors)}
          value={row.cleansingColors}
          colors={colors.cleansingColors}
          getProperties={colors.getCleansingProperties}
          getHex={colors.getCleansingHex}
          loading={colors.colorsLoading}
          onChange={(v) => onChange("cleansingColors", v)}
        />
        <ColourStepCard
          title="2. Energise"
          kind="energising"
          chakra={row.chakra}
          picks={picksFor(row.chakra, "energising", colors.energisingColors)}
          value={row.energisingColors}
          colors={colors.energisingColors}
          getProperties={colors.getEnergisingProperties}
          getHex={colors.getEnergisingHex}
          loading={colors.colorsLoading}
          onChange={(v) => onChange("energisingColors", v)}
        />
      </div>
    </div>
  );
}
