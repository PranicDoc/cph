import { useState, useRef, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { DayPicker } from "react-day-picker";
import type { DateRange } from "react-day-picker";
import { cn } from "../lib/utils";

type Preset = "7d" | "30d" | "90d" | "custom";

export function DateRangeFilter() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activePeriod = (searchParams.get("period") || "30d") as Preset;
  const [showPicker, setShowPicker] = useState(false);
  const [customRange, setCustomRange] = useState<DateRange | undefined>();
  const popupRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (popupRef.current && !popupRef.current.contains(e.target as Node)) {
        setShowPicker(false);
      }
    }
    if (showPicker) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [showPicker]);

  const applyRange = () => {
    if (customRange?.from && customRange?.to) {
      setSearchParams({
        period: "custom",
        from: customRange.from.toISOString().slice(0, 10),
        to: customRange.to.toISOString().slice(0, 10),
      });
    }
    setShowPicker(false);
  };

  const pillClass = (active: boolean) =>
    cn(
      "px-4 py-2 rounded-full text-sm font-normal transition-colors cursor-pointer",
      active
        ? "bg-primary text-white"
        : "border border-border text-text-light bg-card hover:border-primary/30"
    );

  return (
    <div className="flex items-center gap-2 flex-wrap">
      <span className="text-sm text-text-light">Date range:</span>

      {(["7d", "30d", "90d"] as const).map((preset) => (
        <button
          key={preset}
          onClick={() => setSearchParams({ period: preset })}
          className={pillClass(activePeriod === preset)}
        >
          {preset}
        </button>
      ))}

      <div className="relative" ref={popupRef}>
        <button
          onClick={() => setShowPicker(!showPicker)}
          className={pillClass(activePeriod === "custom")}
        >
          Custom
        </button>

        {showPicker && (
          <div className="absolute top-full left-0 mt-2 z-50 bg-card rounded-2xl shadow-xl border border-border p-4 min-w-max">
            <DayPicker
              mode="range"
              selected={customRange}
              onSelect={setCustomRange}
            />
            <div className="flex justify-end gap-2 mt-4">
              <span
                className="text-sm text-text-light hover:text-text cursor-pointer select-none"
                onClick={() => setShowPicker(false)}
              >
                Cancel
              </span>
              <button
                onClick={applyRange}
                disabled={!customRange?.from || !customRange?.to}
                className="bg-primary text-white rounded-lg px-4 py-2 text-sm font-semibold hover:bg-primary-dark transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Apply range
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
