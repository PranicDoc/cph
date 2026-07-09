import { useState, useRef, useEffect } from "react";
import { DayPicker } from "react-day-picker";
import type { DateRange } from "react-day-picker";
import { cn } from "../lib/utils";
import { Button } from "@/components/ui/button";
import type { ChartPeriod } from "../types/chart";

interface ChartPeriodPickerProps {
  value: ChartPeriod;
  onChange: (period: ChartPeriod) => void;
}

export function ChartPeriodPicker({ value, onChange }: ChartPeriodPickerProps) {
  const [showPicker, setShowPicker] = useState(false);
  const [customRange, setCustomRange] = useState<DateRange | undefined>();
  const popupRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (popupRef.current && !popupRef.current.contains(e.target as Node)) {
        setShowPicker(false);
      }
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setShowPicker(false);
    }
    if (showPicker) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleEscape);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [showPicker]);

  function toLocalDateString(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, "0");
    const d = String(date.getDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }

  const applyRange = () => {
    if (customRange?.from && customRange?.to) {
      onChange({
        period: "custom",
        from: toLocalDateString(customRange.from),
        to: toLocalDateString(customRange.to),
      });
    }
    setShowPicker(false);
  };

  const pillClass = (active: boolean) =>
    cn(
      "h-auto px-4 py-1 rounded-full text-xs font-normal transition-colors cursor-pointer",
      active
        ? "bg-primary text-white hover:bg-primary hover:text-white"
        : "border border-border text-text-light bg-card hover:border-primary/30 hover:bg-card hover:text-text-light"
    );

  return (
    <div className="flex items-center gap-2">
      {(["7d", "30d", "90d"] as const).map((preset) => (
        <Button
          key={preset}
          variant="ghost"
          onClick={() => onChange({ period: preset })}
          className={pillClass(value.period === preset)}
        >
          {preset}
        </Button>
      ))}

      <div className="relative" ref={popupRef}>
        <Button
          variant="ghost"
          onClick={() => setShowPicker(!showPicker)}
          className={pillClass(value.period === "custom")}
        >
          Custom
        </Button>

        {showPicker && (
          <div className="absolute top-full right-0 mt-2 z-50 bg-card rounded-2xl shadow-xl border border-border p-4 min-w-max">
            <DayPicker
              mode="range"
              selected={customRange}
              onSelect={setCustomRange}
            />
            <div className="flex justify-end gap-2 mt-4">
              <Button
                type="button"
                variant="ghost"
                className="h-auto p-0 text-xs font-normal text-text-light hover:text-text hover:bg-transparent"
                onClick={() => setShowPicker(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={applyRange}
                disabled={!customRange?.from || !customRange?.to}
                className="h-auto px-4 py-2 text-xs font-semibold"
              >
                Apply range
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
