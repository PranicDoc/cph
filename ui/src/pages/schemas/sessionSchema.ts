// src/schemas/sessionSchema.ts
import { FormSchema } from "../../components/DynamicFormEngine";
import type { Protocol } from "../../hooks/admin/useMasterData";
import {
  hasChiefComplaint,
  hasProtocol,
  CHIEF_COMPLAINT_REQUIRED,
  PROTOCOL_REQUIRED,
} from "./sessionValidation";

export const COMMON_SYMPTOMS = [
  "Headache",
  "Fever",
  "Cough",
  "Fatigue",
  "Nausea",
  "Dizziness",
];

export function buildSessionSchema(protocols: Protocol[], isDraft: boolean): FormSchema {
  return [
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "medicalCondition",
        label: "Chief Complaint",
        type: "textarea",
        placeholder: "E.g. Stomachache for 4 days or Neck pain for 5 days...",
        required: true,
        validate: (_value, formValues) =>
          hasChiefComplaint(formValues) ? undefined : CHIEF_COMPLAINT_REQUIRED,
      },
      {
        name: "labFindings",
        label: "Lab Findings",
        type: "file",
        readOnly: !isDraft,
        confirmRemove: true,
      },
    ],
  },
  {
    fieldsContainerClassName: "space-y-4 mt-4",
    fields: [
      {
        name: "symptoms",
        label: "Symptoms",
        type: "tag",
        placeholder: "Type to add symptoms...",
        suggestions: COMMON_SYMPTOMS,
        displayKey: "symptomName",
        createNewTag: (name: string) => ({
          symptomName: name,
          severity: 1, // default
          description: "",
        }),
      },
    ],
  },
  {
    fieldsContainerClassName: "space-y-4 mt-4",
    fields: [
      {
        name: "medications",
        label: "Medications",
        type: "table",
        readOnly: !isDraft,
        displayKey: "medicationName",
        editableFields: {
          dosage: { label: "Dosage", placeholder: "e.g. 500mg" },
          timing: {
            label: "When to take",
            type: "checkbox-group" as const,
            options: [
              { label: "Morning", value: "morningDose" },
              { label: "Afternoon", value: "afternoonDose" },
              { label: "Night", value: "nightDose" },
            ],
          },
        },
        createNewTag: (name: string) => ({
          medicationName: name,
          dosage: "",
          morningDose: false,
          afternoonDose: false,
          nightDose: false,
        }),
      },
    ],
  },
  {
    containerClassName: "bg-background rounded-xl p-4 mt-4",
    fieldsContainerClassName: "grid grid-cols-2 gap-6",
    title: "Protocol Details",
    fields: [
      {
        name: "healingDetails.selectedProtocols",
        label: "Protocols",
        type: "multiselect",
        // Not `required: true` (that path returns a generic "Required" for an
        // empty array) — this cross-field check also accepts free-text protocol.
        validate: (_value, formValues) =>
          hasProtocol(formValues) ? undefined : PROTOCOL_REQUIRED,
        wrapperClassName: "col-span-1",
        multiSelectPlaceholder: "Select protocols...",
        multiSelectOptions: protocols,
        getOptionKey: (p: Protocol) => p.id,
        getOptionLabel: (p: Protocol) => p.name,
        renderBadge: (p: Protocol) => (p.sourceBook ? `${p.name} — ${p.sourceBook}, p.${p.pageNumber}` : p.name),
      },
      {
        name: "healingDetails.detailedProtocol",
        label: "Marilltte Protocol",
        type: "textarea",
        wrapperClassName: "row-span-2",
        rows: 4,
      }
    ],
  },
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "complementaryDetails",
        label: "Complementary Modalities",
        type: "textarea",
        placeholder: "Please mention complementary modalities if any...",
        enableToggle: true,
      },
      {
        name: "infectiousDetails",
        label: "Infectious Illness",
        type: "textarea",
        placeholder: "Please mention infectious illness if any...",
        enableToggle: true,
      },
    ],
  },
  ];
}
