import { useState } from "react";
import { ChevronDown } from "lucide-react";
import type { HPUTable } from "@/interfaces";
import { ColorChipPicker } from "./ColorChipPicker";

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
  if (e.length) parts.push(`Energising: ${e.join(", ")}`);
  if (c.length) parts.push(`Cleansing: ${c.join(", ")}`);
  return parts.join(" · ");
}

/** New-view step 1 of the close-case dialog: one accordion row per scanned chakra. */
export default function ProtocolUnderstanding({ rows, energisingColors, cleansingColors, colorsLoading, getEnergisingProperties, getCleansingProperties, getEnergisingHex, getCleansingHex, onChange }: Props) {
  const [open, setOpen] = useState<string | null>(rows[0]?.chakra ?? null);
  return (
    <div className="hl-acc">
      {rows.map((row, index) => {
        const filled = row.energisingColors.length + row.cleansingColors.length > 0;
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
              <span className={`hl-badge ${filled ? "hl-badge-ok" : "hl-badge-off"}`}>{filled ? "Filled in" : "Not filled"}</span>
              {!isOpen && <span className="hl-acc-sum">{summary(row)}</span>}
              <ChevronDown className="hl-acc-chev" aria-hidden="true" />
            </button>
            {isOpen && (
              <div className="hl-acc-body">
                <ColorChipPicker
                  label="Energising colors"
                  value={row.energisingColors}
                  colors={energisingColors}
                  getProperties={getEnergisingProperties}
                  getColorHex={getEnergisingHex}
                  loading={colorsLoading}
                  onChange={(v) => onChange(index, "energisingColors", v)}
                />
                <ColorChipPicker
                  label="Cleansing colors"
                  value={row.cleansingColors}
                  colors={cleansingColors}
                  getProperties={getCleansingProperties}
                  getColorHex={getCleansingHex}
                  loading={colorsLoading}
                  onChange={(v) => onChange(index, "cleansingColors", v)}
                />
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
