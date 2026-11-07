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
  picksFor: (chakra: string, category: UsualPickCategory) => UsualPick[];
  onChange: <K extends keyof HPUTable>(field: K, value: HPUTable[K]) => void;
}

/** One chakra on the close-case page: why the healer worked on it, then 1. Cleanse and 2. Energise. */
export default function ChakraDetail({ row, colors, picksFor, onChange }: Props) {
  const id = `reason-${row.chakra.replace(/\s+/g, "-")}`;

  return (
    <div className="flex flex-col gap-[18px]">
      <div className="flex flex-col gap-1.5">
        <label className="hl-step-q" htmlFor={id}>
          Why did you work on this chakra? <span className="text-danger" aria-hidden="true">*</span>
        </label>
        <Textarea
          id={id}
          rows={2}
          className="resize-none"
          value={row.reason ?? ""}
          onChange={(e) => onChange("reason", e.target.value)}
          maxLength={TEXT_LIMITS.chakraReason}
          required
        />
        <CharacterCount value={row.reason} max={TEXT_LIMITS.chakraReason} />
      </div>
      <div className="hl-steps">
        <ColourStepCard
          title="1. Cleanse"
          kind="cleansing"
          chakra={row.chakra}
          picks={picksFor(row.chakra, "cleansing")}
          value={row.cleansingColors}
          colors={colors.cleansingColors}
          getProperties={colors.getCleansingProperties}
          getHex={colors.getCleansingHex}
          onChange={(v) => onChange("cleansingColors", v)}
        />
        <ColourStepCard
          title="2. Energise"
          kind="energising"
          chakra={row.chakra}
          picks={picksFor(row.chakra, "energising")}
          value={row.energisingColors}
          colors={colors.energisingColors}
          getProperties={colors.getEnergisingProperties}
          getHex={colors.getEnergisingHex}
          onChange={(v) => onChange("energisingColors", v)}
        />
      </div>
    </div>
  );
}
