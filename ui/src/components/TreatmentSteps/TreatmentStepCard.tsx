import { GripVertical, X } from "lucide-react";
import { TagInput } from "../TagInput";
import type { TreatmentStep } from "../../interfaces";
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
        <select
          value={step.tag}
          onChange={(e) => onUpdate(index, "tag", e.target.value)}
          className="flex-1 sm:flex-none sm:w-44 p-2 border border-border rounded-lg text-xs font-semibold outline-none"
        >
          {TECHNIQUES.map((technique) => (
            <option key={technique} value={technique}>
              {technique}
            </option>
          ))}
        </select>

        <div className="flex-1" />

        <button
          type="button"
          onClick={() => onRemove(index)}
          className="text-text-light hover:text-danger"
          aria-label={`Remove step ${step.step}`}
        >
          <X className="w-4 h-4" />
        </button>
        <span className="text-text-light cursor-grab">
          <GripVertical className="w-4 h-4" />
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
        {showSweepType && (
          <div>
            <label className="text-xs font-semibold text-text-light mb-1 block">
              Sweep Type
            </label>
            <select
              value={step.sweepType || ""}
              onChange={(e) => onUpdate(index, "sweepType", e.target.value)}
              className="w-full p-2 border border-border rounded-lg text-xs outline-none"
            >
              <option value="">Select...</option>
              {SWEEP_TYPES.map((sweepType) => (
                <option key={sweepType} value={sweepType}>
                  {sweepType}
                </option>
              ))}
            </select>
          </div>
        )}

        {showTargets && (
          <div className="col-span-1 sm:col-span-2 md:col-span-2">
            <label className="text-xs font-semibold text-text-light mb-1 block">
              Target(s)
            </label>
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
            <label className="text-xs font-semibold text-text-light mb-1 block">
              Prana Color
            </label>
            <select
              value={step.color || ""}
              onChange={(e) => onUpdate(index, "color", e.target.value)}
              className="w-full p-2 border border-border rounded-lg text-xs outline-none"
            >
              <option value="">Select...</option>
              {PRANA_COLORS.map((color) => (
                <option key={color} value={color}>
                  {color}
                </option>
              ))}
            </select>
          </div>
        )}

        {showCycles && (
          <div>
            <label className="text-xs font-semibold text-text-light mb-1 block">
              Cycles
            </label>
            <input
              type="number"
              min={1}
              value={step.cycles || ""}
              onChange={(e) =>
                onUpdate(index, "cycles", Number(e.target.value) || undefined)
              }
              placeholder="e.g., 30"
              className="w-full p-2 border border-border rounded-lg text-xs outline-none"
            />
          </div>
        )}

        <div className="col-span-1 sm:col-span-2 lg:col-span-2">
          <label className="text-xs font-semibold text-text-light mb-1 block">
            Notes / Override
          </label>
          <input
            type="text"
            value={step.notes || ""}
            onChange={(e) => onUpdate(index, "notes", e.target.value)}
            placeholder="Optional description..."
            className="w-full p-2 border border-border rounded-lg text-xs outline-none"
          />
        </div>
      </div>
    </div>
  );
}
