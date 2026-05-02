// src/schemas/sessionSchema.ts
import { FormSchema } from "../components/DynamicFormEngine";

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
        label: "Medical Condition / Diagnosis",
        type: "textarea",
        placeholder: "Please state the main ailment that will be treated...",
        required: true,
      },
      {
        name: "labFindings",
        label: "Lab Findings",
        type: "textarea",
        placeholder: "Please mention lab findings if any...",
      },
    ],
  },
  {
    fieldsContainerClassName: "space-y-4 mt-4",
    fields: [
      {
        name: "symptoms",
        label: "Chief Complaint / Symptoms",
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
        type: "tag",
        placeholder: "Type to add more medications...",
        displayKey: "medicationName",
        createNewTag: (name: string) => ({
          medicationName: name,
          dosage: "",
          frequency: "",
          notes: "",
        }),
      },
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
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "healerNotes",
        label: "Healer Notes (Private)",
        type: "textarea",
        rows: 3,
        placeholder: "Session observations...",
      },
      {
        name: "patientFeedback",
        label: "Patient Feedback",
        type: "textarea",
        rows: 3,
        placeholder: "How does the patient feel?",
      },
    ],
  },
];
