import type { TreatmentStep } from "../interfaces";
import { TreatmentPlanHeader } from "./TreatmentSteps/TreatmentPlanHeader";
import { TreatmentStepCard } from "./TreatmentSteps/TreatmentStepCard";

interface TreatmentPlan {
  protocolName: string;
  bookReference: string;
  treatmentSteps: TreatmentStep[];
}

interface TreatmentStepsProps {
  value: TreatmentPlan;
  onChange: (plan: TreatmentPlan) => void;
}

function renumberSteps(steps: TreatmentStep[]) {
  return steps.map((step, index) => ({ ...step, step: index + 1 }));
}

export function TreatmentSteps({ value, onChange }: TreatmentStepsProps) {
  const { protocolName, bookReference, treatmentSteps } = value;

  const updateStep = (index: number, field: keyof TreatmentStep, val: any) => {
    const updatedSteps = treatmentSteps.map((step, currentIndex) =>
      currentIndex === index ? { ...step, [field]: val } : step
    );

    onChange({ ...value, treatmentSteps: updatedSteps });
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
    const updatedSteps = renumberSteps(
      treatmentSteps.filter((_, currentIndex) => currentIndex !== index)
    );

    onChange({ ...value, treatmentSteps: updatedSteps });
  };

  return (
    <div className="space-y-4">
      <TreatmentPlanHeader
        protocolName={protocolName}
        bookReference={bookReference}
        onProtocolNameChange={(name) =>
          onChange({ ...value, protocolName: name })
        }
        onBookReferenceChange={(ref) =>
          onChange({ ...value, bookReference: ref })
        }
      />

      {/* <div className="bg-background rounded-xl p-3 space-y-3">
        {treatmentSteps.map((step, index) => (
          <TreatmentStepCard
            key={index}
            step={step}
            index={index}
            onUpdate={updateStep}
            onRemove={removeStep}
          />
        ))}

        <button
          type="button"
          onClick={addStep}
          className="w-full py-3 border-2 border-dashed border-border rounded-lg text-sm font-medium text-text-light hover:border-primary hover:text-primary transition-colors"
        >
          + Add Treatment Step
        </button>
      </div> */}
    </div>
  );
}
