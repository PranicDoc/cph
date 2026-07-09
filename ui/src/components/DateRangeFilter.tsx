import { useState, useRef, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { DayPicker } from "react-day-picker";
import type { DateRange } from "react-day-picker";
import { cn } from "../lib/utils";
import { Button } from "@/components/ui/button";

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
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set("period", "custom");
        next.set("from", toLocalDateString(customRange.from!));
        next.set("to", toLocalDateString(customRange.to!));
        return next;
      });
    }
    setShowPicker(false);
  };

  const pillClass = (active: boolean) =>
    cn(
      "h-auto px-4 py-2 rounded-full text-sm font-normal transition-colors cursor-pointer",
      active
        ? "bg-primary text-white hover:bg-primary hover:text-white"
        : "border border-border text-text-light bg-card hover:border-primary/30 hover:bg-card hover:text-text-light"
    );

  return (
    <div className="flex items-center gap-2 flex-wrap">
      <span className="text-sm text-text-light">Date range:</span>

      {(["7d", "30d", "90d"] as const).map((preset) => (
        <Button
          key={preset}
          variant="ghost"
          onClick={() =>
            setSearchParams((prev) => {
              const next = new URLSearchParams(prev);
              next.set("period", preset);
              next.delete("from");
              next.delete("to");
              return next;
            })
          }
          className={pillClass(activePeriod === preset)}
        >
          {preset}
        </Button>
      ))}

      <div className="relative" ref={popupRef}>
        <Button
          variant="ghost"
          onClick={() => setShowPicker(!showPicker)}
          className={pillClass(activePeriod === "custom")}
        >
          Custom
        </Button>

        {showPicker && (
          <div className="absolute top-full left-0 mt-2 z-50 bg-card rounded-2xl shadow-xl border border-border p-4 min-w-max">
            <DayPicker
              mode="range"
              selected={customRange}
              onSelect={setCustomRange}
            />
            <div className="flex justify-end gap-2 mt-4">
              <Button
                type="button"
                variant="ghost"
                className="h-auto p-0 text-sm font-normal text-text-light hover:text-text hover:bg-transparent"
                onClick={() => setShowPicker(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={applyRange}
                disabled={!customRange?.from || !customRange?.to}
                className="h-auto px-4 py-2 font-semibold"
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
