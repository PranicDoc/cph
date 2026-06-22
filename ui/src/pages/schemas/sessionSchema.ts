// src/schemas/sessionSchema.ts
import { FormSchema } from "../../components/DynamicFormEngine";

export const COMMON_SYMPTOMS = [
  "Headache",
  "Fever",
  "Cough",
  "Fatigue",
  "Nausea",
  "Dizziness",
];

export const sessionSchema: FormSchema = [
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "medicalCondition",
        label: "Chief Complaint",
        type: "textarea",
        placeholder: "Please state the main ailment that will be treated...",
        required: true,
      },
      {
        name: "labFindings",
        label: "Lab Findings",
        type: "file",
        readOnly: true,
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
        readOnly: true,
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
        name: "healingDetails.protocolName",
        label: "Protocol Name",
        wrapperClassName: "col-span-1",
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
    containerClassName: "bg-background rounded-xl p-4 mt-4",
    fieldsContainerClassName: "flex flex-wrap justify-around gap-4",
    title: "Book page numbers",
    fields: [
      { name: "basicBookPg", label: "Basic Book pg no", type: "number" },
      {
        name: "advancedBookPg",
        label: "Advanced Book pg no",
        type: "number",
      },
      {
        name: "psychotherapyPg",
        label: "Psychotherapy Book pg no",
        type: "number",
      },
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
