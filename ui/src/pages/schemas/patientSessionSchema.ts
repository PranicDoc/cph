import { FormSchema } from "../../components/DynamicFormEngine";
import { COMMON_SYMPTOMS } from "./sessionSchema";

export const patientSessionSchema: FormSchema = [
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "medicalCondition",
        label: "Chief Complaint",
        type: "textarea",
        placeholder: "Please state the main ailment that will be treated...", // TODO add examples
        required: true,
      },
      {
        name: "labFindings",
        label: "Lab Findings",
        type: "file",
        placeholder: "Please mention lab findings if any...",
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
        required: true,
        displayKey: "symptomName",
        createNewTag: (name: string) => ({
          symptomName: name,
          severity: 1,
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
        placeholder: "Type to add more medications...",
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
        display: false,
        wrapperClassName: "col-span-1",
      },
      {
        name: "healingDetails.detailedProtocol",
        label: "Marilltte Protocol",
        type: "textarea",
        display: false,
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
      { name: "basicBookPg", label: "Basic Book pg no", type: "number", display: false },
      { name: "advancedBookPg", label: "Advanced Book pg no", type: "number", display: false },
      { name: "psychotherapyPg", label: "Psychotherapy Book pg no", type: "number", display: false },
    ],
  },
  {
    fieldsContainerClassName: "grid grid-cols-1 md:grid-cols-2 gap-4 mt-4",
    fields: [
      {
        name: "complementaryDetails",
        label: "Complementary Modalities (Treatments other than main doctor consulting)",
        type: "textarea",
        placeholder: "Example: Accupunture, Cupping, Osteopathy, Flower medicine...",
        enableToggle: true,
        requiredWhenEnabled: true,
      },
      {
        name: "infectiousDetails",
        label: "Infectious Illness",
        type: "textarea",
        placeholder: "Example: Cold...",
        enableToggle: true,
        requiredWhenEnabled: true,
      },
    ],
  },
];
