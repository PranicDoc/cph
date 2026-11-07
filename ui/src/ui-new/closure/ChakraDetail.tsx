import { useState } from "react";
import type { HPUTable } from "@/interfaces";
import type { useProtocolColors } from "@/hooks/useProtocolUnderstanding";
import { Textarea } from "@/components/ui/textarea";
import { CharacterCount } from "@/components/form/CharacterCount";
import { TEXT_LIMITS } from "@/lib/textLimits";
import { ColorChipPicker } from "./ColorChipPicker";

interface Props {
  row: HPUTable;
  colors: ReturnType<typeof useProtocolColors>;
  onChange: <K extends keyof HPUTable>(field: K, value: HPUTable[K]) => void;
}

/** One chakra on the close-case page: why the healer worked on it, then the colours used. */
export default function ChakraDetail({ row, colors, onChange }: Props) {
  const picked = [...row.cleansingColors, ...row.energisingColors];
  // Properties are optional: shown when some are saved already, else behind a link.
  const [showProperties, setShowProperties] = useState(picked.some((c) => c.properties.length > 0));
  const id = `reason-${row.chakra.replace(/\s+/g, "-")}`;

  return (
    <div className="space-y-4">
      <div className="space-y-1.5">
        <label className="lbl" htmlFor={id}>
          Why did you work on this chakra? <span className="text-danger" aria-hidden="true">*</span>
        </label>
        <Textarea
          id={id}
          value={row.reason ?? ""}
          onChange={(e) => onChange("reason", e.target.value)}
          maxLength={TEXT_LIMITS.chakraReason}
          required
        />
        <CharacterCount value={row.reason} max={TEXT_LIMITS.chakraReason} />
      </div>
      <ColorChipPicker
        label="Cleansing colours"
        value={row.cleansingColors}
        colors={colors.cleansingColors}
        getProperties={colors.getCleansingProperties}
        getColorHex={colors.getCleansingHex}
        loading={colors.colorsLoading}
        onChange={(v) => onChange("cleansingColors", v)}
        singleProperty
        showProperties={showProperties}
      />
      <ColorChipPicker
        label="Energising colours"
        value={row.energisingColors}
        colors={colors.energisingColors}
        getProperties={colors.getEnergisingProperties}
        getColorHex={colors.getEnergisingHex}
        loading={colors.colorsLoading}
        onChange={(v) => onChange("energisingColors", v)}
        singleProperty
        showProperties={showProperties}
      />
      {!showProperties && picked.length > 0 && (
        <button type="button" className="hl-link" onClick={() => setShowProperties(true)}>
          + Add properties for the chosen colours (optional)
        </button>
      )}
    </div>
  );
}
