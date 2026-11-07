import { useState } from "react";
import { ChevronDown } from "lucide-react";
import type { HPUTable } from "@/interfaces";
import { ColorChipPicker } from "./ColorChipPicker";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { CharacterCount } from "@/components/form/CharacterCount";
import { TEXT_LIMITS } from "@/lib/textLimits";

interface Props {
  rows: HPUTable[];
  energisingColors: string[];
  cleansingColors: string[];
  colorsLoading: boolean;
  getEnergisingProperties: (color: string) => string[];
  getCleansingProperties: (color: string) => string[];
  getEnergisingHex: (color: string) => string | undefined;
  getCleansingHex: (color: string) => string | undefined;
  onChange: <K extends keyof HPUTable>(index: number, field: K, value: HPUTable[K]) => void;
}

function summary(row: HPUTable) {
  const e = row.energisingColors.map((c) => c.color);
  const c = row.cleansingColors.map((x) => x.color);
  if (e.length === 0 && c.length === 0) return "Not filled in";
  const parts = [];
  if (c.length) parts.push(`Cleansing: ${c.join(", ")}`);
  if (e.length) parts.push(`Energising: ${e.join(", ")}`);
  return parts.join(" · ");
}

/** New-view protocol understanding: one accordion row per scanned chakra, its reason first. */
export default function ProtocolUnderstanding({ rows, energisingColors, cleansingColors, colorsLoading, getEnergisingProperties, getCleansingProperties, getEnergisingHex, getCleansingHex, onChange }: Props) {
  const [open, setOpen] = useState<string | null>(rows[0]?.chakra ?? null);
  return (
    <div className="hl-acc">
      {rows.map((row, index) => {
        const filled = !!row.reason?.trim(); // the reason is required; colours are optional
        const isOpen = open === row.chakra;
        return (
          <section key={row.chakra} className="hl-acc-item" data-open={isOpen}>
            <button
              type="button"
              className="hl-acc-head"
              aria-expanded={isOpen}
              onClick={() => setOpen(isOpen ? null : row.chakra)}
            >
              <span className="hl-acc-name">{row.chakra}</span>
              <span className={`hl-badge ${filled ? "hl-badge-ok" : "hl-badge-off"}`}>{filled ? "Filled in" : "Reason needed"}</span>
              {!isOpen && <span className="hl-acc-sum">{summary(row)}</span>}
              <ChevronDown className="hl-acc-chev" aria-hidden="true" />
            </button>
            {isOpen && (
              <div className="hl-acc-body">
                <div className="space-y-1.5">
                  <Label htmlFor={`reason-${index}`}>Why this chakra?</Label>
                  <Textarea
                    id={`reason-${index}`}
                    value={row.reason ?? ""}
                    onChange={(e) => onChange(index, "reason", e.target.value)}
                    maxLength={TEXT_LIMITS.chakraReason}
                    required
                  />
                  <CharacterCount value={row.reason} max={TEXT_LIMITS.chakraReason} />
                </div>
                <ColorChipPicker
                  label="Cleansing colors"
                  value={row.cleansingColors}
                  colors={cleansingColors}
                  getProperties={getCleansingProperties}
                  getColorHex={getCleansingHex}
                  loading={colorsLoading}
                  onChange={(v) => onChange(index, "cleansingColors", v)}
                />
                <ColorChipPicker
                  label="Energising colors"
                  value={row.energisingColors}
                  colors={energisingColors}
                  getProperties={getEnergisingProperties}
                  getColorHex={getEnergisingHex}
                  loading={colorsLoading}
                  onChange={(v) => onChange(index, "energisingColors", v)}
                />
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
