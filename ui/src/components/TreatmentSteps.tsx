import { X, GripVertical } from "lucide-react";
import { TagInput } from "./TagInput"; // your enhanced tag input
import { useState } from "react";
import { TreatmentStep } from "../interfaces";

interface TreatmentPlan {
  protocolName: string;
  bookReference: string;
  treatmentSteps: TreatmentStep[];
}

interface TreatmentStepsProps {
  value: TreatmentPlan;
  onChange: (plan: TreatmentPlan) => void;
}

const TECHNIQUES = [
  "Scanning",
  "General Sweeping",
  "Localized Sweeping",
  "Energizing",
  "Stabilizing",
  "Inhibiting",
  "Cutting",
  "Disintegrating",
  "Cleansing",
  "Invocation / Prayer",
  "Releasing",
  "Shielding / Sealing",
];

const SWEEP_TYPES = [
  "Downward Sweeping",
  "Upward Sweeping",
  "Counter‑clockwise Sweeping",
  "Full Body Sweep (Front)",
  "Full Body Sweep (Back)",
  "Full Body Sweep (Side)",
  "Aura Sweeping",
];

const PRANA_COLORS = [
  "LWG",
  "LWV",
  "LWB",
  "Light Whitish Green",
  "Light Whitish Blue",
  "Ordinary Light Whitish Violet",
  "Electric Violet",
  "Gold",
];

const CHAKRA_SUGGESTIONS = [
  "Crown",
  "Forehead",
  "Ajna",
  "Throat",
  "Heart",
  "Solar Plexus",
  "Navel",
  "Basic",
  "Back Head Minor",
  "Eyes",
  "Temples",
  "Spine",
  "Head",
  "Neck",
];

export function TreatmentSteps({ value, onChange }: TreatmentStepsProps) {
  const { protocolName, bookReference, treatmentSteps } = value;

  const updateProtocolName = (name: string) => {
    onChange({ ...value, protocolName: name });
  };

  const updateBookReference = (ref: string) => {
    onChange({ ...value, bookReference: ref });
  };

  const updateStep = (index: number, field: keyof TreatmentStep, val: any) => {
    const updatedSteps = treatmentSteps.map((step, i) =>
      i === index ? { ...step, [field]: val } : step
    );
    onChange({ ...value, treatmentSteps: updatedSteps });
  };

  const updateTargets = (index: number, newTargets: string[]) => {
    updateStep(index, "targets", newTargets);
  };

  const addStep = () => {
    const newStep: TreatmentStep = {
      step: treatmentSteps.length + 1,
      tag: "General Sweeping",
      targets: [],
    };
    onChange({ ...value, treatmentSteps: [...treatmentSteps, newStep] });
  };

  const removeStep = (index: number) => {
    const updatedSteps = treatmentSteps
      .filter((_, i) => i !== index)
      .map((step, i) => ({ ...step, num: i + 1 }));
    onChange({ ...value, treatmentSteps: updatedSteps });
  };

  return (
    <div className="space-y-4">
      {/* Protocol Header */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-semibold text-text-light mb-2">
            Protocol Name
          </label>
          <input
            type="text"
            value={protocolName}
            onChange={(e) => updateProtocolName(e.target.value)}
            placeholder="e.g., Headache Relief"
            className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none"
          />
        </div>
        <div>
          <label className="block text-sm font-semibold text-text-light mb-2">
            Book Reference
          </label>
          <input
            type="text"
            value={bookReference}
            onChange={(e) => updateBookReference(e.target.value)}
            placeholder="e.g., Advanced Pranic Healing p. 77"
            className="w-full p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none"
          />
        </div>
      </div>

      {/* Steps List */}
      <div className="bg-background rounded-xl p-3 space-y-3">
        {treatmentSteps.map((step, idx) => (
          <div
            key={idx}
            className="bg-white border border-border rounded-lg p-4"
          >
            {/* Top row: number, technique, and actions */}
            <div className="flex items-center gap-2 mb-3">
              <span className="font-bold text-primary w-6">{step.step}.</span>
              <select
                value={step.tag}
                onChange={(e) => updateStep(idx, "tag", e.target.value)}
                className="flex-1 sm:flex-none sm:w-44 p-2 border border-border rounded-lg text-xs font-semibold outline-none"
              >
                {TECHNIQUES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
              <div className="flex-1" /> {/* spacer */}
              <button
                type="button"
                onClick={() => removeStep(idx)}
                className="text-text-light hover:text-danger"
              >
                <X className="w-4 h-4" />
              </button>
              <span className="text-text-light cursor-grab">
                <GripVertical className="w-4 h-4" />
              </span>
            </div>

            {/* Detail fields grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
              {/* Sweep Type (only for sweeping techniques) */}
              {(step.tag === "General Sweeping" ||
                step.tag === "Localized Sweeping") && (
                <div>
                  <label className="text-xs font-semibold text-text-light mb-1 block">
                    Sweep Type
                  </label>
                  <select
                    value={step.sweepType || ""}
                    onChange={(e) =>
                      updateStep(idx, "sweepType", e.target.value)
                    }
                    className="w-full p-2 border border-border rounded-lg text-xs outline-none"
                  >
                    <option value="">Select…</option>
                    {SWEEP_TYPES.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Targets (tag input for chakras/areas) */}
              {(step.tag === "Localized Sweeping" ||
                step.tag === "Energizing" ||
                step.tag === "Scanning" ||
                step.tag === "Stabilizing") && (
                <div className="col-span-1 sm:col-span-2 md:col-span-2">
                  <label className="text-xs font-semibold text-text-light mb-1 block">
                    Target(s)
                  </label>
                  <TagInput
                    value={step.targets || []}
                    onChange={(targets) => updateTargets(idx, targets)}
                    suggestions={CHAKRA_SUGGESTIONS}
                    placeholder="Add chakra / area…"
                  />
                </div>
              )}

              {/* Prana Color (for energizing) */}
              {step.tag === "Energizing" && (
                <div>
                  <label className="text-xs font-semibold text-text-light mb-1 block">
                    Prana Color
                  </label>
                  <select
                    value={step.color || ""}
                    onChange={(e) => updateStep(idx, "color", e.target.value)}
                    className="w-full p-2 border border-border rounded-lg text-xs outline-none"
                  >
                    <option value="">Select…</option>
                    {PRANA_COLORS.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Cycles */}
              {(step.tag === "General Sweeping" ||
                step.tag === "Localized Sweeping" ||
                step.tag === "Energizing") && (
                <div>
                  <label className="text-xs font-semibold text-text-light mb-1 block">
                    Cycles
                  </label>
                  <input
                    type="number"
                    min={1}
                    value={step.cycles || ""}
                    onChange={(e) =>
                      updateStep(
                        idx,
                        "cycles",
                        Number(e.target.value) || undefined
                      )
                    }
                    placeholder="e.g., 30"
                    className="w-full p-2 border border-border rounded-lg text-xs outline-none"
                  />
                </div>
              )}

              {/* Optional manual description (always visible) */}
              <div className="col-span-1 sm:col-span-2 lg:col-span-2">
                <label className="text-xs font-semibold text-text-light mb-1 block">
                  Notes / Override
                </label>
                <input
                  type="text"
                  value={step.notes || ""}
                  onChange={(e) => updateStep(idx, "notes", e.target.value)}
                  placeholder="Optional description…"
                  className="w-full p-2 border border-border rounded-lg text-xs outline-none"
                />
              </div>
            </div>
          </div>
        ))}

        {/* Add button */}
        <button
          type="button"
          onClick={addStep}
          className="w-full py-3 border-2 border-dashed border-border rounded-lg text-sm font-medium text-text-light hover:border-primary hover:text-primary transition-colors"
        >
          + Add Treatment Step
        </button>
      </div>
    </div>
  );
}
