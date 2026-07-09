import { GripVertical, X } from "lucide-react";
import { TagInput } from "../TagInput";
import type { TreatmentStep } from "../../interfaces";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  CHAKRA_SUGGESTIONS,
  CYCLE_TECHNIQUES,
  PRANA_COLORS,
  SWEEP_TYPES,
  SWEEPING_TECHNIQUES,
  TARGETED_TECHNIQUES,
  TECHNIQUES,
} from "./options";

interface TreatmentStepCardProps {
  step: TreatmentStep;
  index: number;
  onUpdate: (index: number, field: keyof TreatmentStep, value: any) => void;
  onRemove: (index: number) => void;
}

function includesTechnique(options: string[], technique: string) {
  return options.includes(technique);
}

const compactSelectCls = "w-full p-2 border border-border rounded-lg text-xs";

export function TreatmentStepCard({
  step,
  index,
  onUpdate,
  onRemove,
}: TreatmentStepCardProps) {
  const showSweepType = includesTechnique(SWEEPING_TECHNIQUES, step.tag);
  const showTargets = includesTechnique(TARGETED_TECHNIQUES, step.tag);
  const showCycles = includesTechnique(CYCLE_TECHNIQUES, step.tag);
  const showPranaColor = step.tag === "Energizing";

  return (
    <div className="bg-white border border-border rounded-lg p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="font-bold text-primary w-6">{step.step}.</span>
        <Select
          value={step.tag}
          onValueChange={(v) => onUpdate(index, "tag", v)}
        >
          <SelectTrigger className="flex-1 sm:flex-none sm:w-44 p-2 border border-border rounded-lg text-xs font-semibold">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {TECHNIQUES.map((technique) => (
              <SelectItem key={technique} value={technique}>
                {technique}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex-1" />

        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          onClick={() => onRemove(index)}
          className="h-auto w-auto p-0 text-text-light hover:text-danger hover:bg-transparent"
          aria-label={`Remove step ${step.step}`}
        >
          <X className="w-4 h-4" />
        </Button>
        <span className="text-text-light cursor-grab">
          <GripVertical className="w-4 h-4" />
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
        {showSweepType && (
          <div>
            <Label className="text-xs font-semibold text-text-light mb-1 block">
              Sweep Type
            </Label>
            <Select
              value={step.sweepType || ""}
              onValueChange={(v) => onUpdate(index, "sweepType", v)}
            >
              <SelectTrigger className={compactSelectCls}>
                <SelectValue placeholder="Select..." />
              </SelectTrigger>
              <SelectContent>
                {SWEEP_TYPES.map((sweepType) => (
                  <SelectItem key={sweepType} value={sweepType}>
                    {sweepType}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {showTargets && (
          <div className="col-span-1 sm:col-span-2 md:col-span-2">
            <Label className="text-xs font-semibold text-text-light mb-1 block">
              Target(s)
            </Label>
            <TagInput
              value={step.targets || []}
              onChange={(targets) => onUpdate(index, "targets", targets)}
              suggestions={CHAKRA_SUGGESTIONS}
              placeholder="Add chakra / area..."
            />
          </div>
        )}

        {showPranaColor && (
          <div>
            <Label className="text-xs font-semibold text-text-light mb-1 block">
              Prana Color
            </Label>
            <Select
              value={step.color || ""}
              onValueChange={(v) => onUpdate(index, "color", v)}
            >
              <SelectTrigger className={compactSelectCls}>
                <SelectValue placeholder="Select..." />
              </SelectTrigger>
              <SelectContent>
                {PRANA_COLORS.map((color) => (
                  <SelectItem key={color} value={color}>
                    {color}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {showCycles && (
          <div>
            <Label className="text-xs font-semibold text-text-light mb-1 block">
              Cycles
            </Label>
            <Input
              type="number"
              min={1}
              value={step.cycles || ""}
              onChange={(e) =>
                onUpdate(index, "cycles", Number(e.target.value) || undefined)
              }
              placeholder="e.g., 30"
              className="p-2 border text-xs"
            />
          </div>
        )}

        <div className="col-span-1 sm:col-span-2 lg:col-span-2">
          <Label className="text-xs font-semibold text-text-light mb-1 block">
            Notes / Override
          </Label>
          <Input
            type="text"
            value={step.notes || ""}
            onChange={(e) => onUpdate(index, "notes", e.target.value)}
            placeholder="Optional description..."
            className="p-2 border text-xs"
          />
        </div>
      </div>
    </div>
  );
}
